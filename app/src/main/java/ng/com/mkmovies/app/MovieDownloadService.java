package ng.com.mkmovies.app;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.webkit.URLUtil;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native resumable foreground downloader with Pause/Resume and Cancel controls. */
public class MovieDownloadService extends Service {
    private static final String TAG = "MKMovieDownload";
    private static final String ACTION_START = "ng.com.mkmovies.app.action.START_MOVIE_DOWNLOAD";
    private static final String ACTION_PAUSE = "ng.com.mkmovies.app.action.PAUSE_MOVIE_DOWNLOAD";
    private static final String ACTION_RESUME = "ng.com.mkmovies.app.action.RESUME_MOVIE_DOWNLOAD";
    private static final String ACTION_CANCEL = "ng.com.mkmovies.app.action.CANCEL_MOVIE_DOWNLOAD";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_FILENAME = "filename";
    private static final String EXTRA_MIME = "mime";
    private static final String EXTRA_USER_AGENT = "user_agent";
    private static final String EXTRA_COOKIES = "cookies";
    private static final String EXTRA_REFERER = "referer";
    public static final String PROGRESS_CHANNEL_ID = "mk_movie_downloads_progress_v5";
    public static final String RESULT_CHANNEL_ID = "mk_movie_downloads_complete_v5";
    private static final int PROGRESS_NOTIFICATION_ID = 4302;
    private static final int RESULT_NOTIFICATION_ID = 4303;
    private static final String PREFS = "mk_movie_download_state";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile boolean running;
    private volatile HttpURLConnection activeConnection;
    private NotificationManager notifications;
    private SharedPreferences state;

    public static void start(Context context, String url, String filename, String mimeType,
                             String userAgent, String cookies, String referer) {
        Intent intent = new Intent(context, MovieDownloadService.class)
                .setAction(ACTION_START).putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_FILENAME, filename).putExtra(EXTRA_MIME, mimeType)
                .putExtra(EXTRA_USER_AGENT, userAgent).putExtra(EXTRA_COOKIES, cookies)
                .putExtra(EXTRA_REFERER, referer);
        startServiceCompat(context, intent);
    }

    private static void startServiceCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        notifications = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        state = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
    }

    @Override public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? "" : safe(intent.getAction());
        if (ACTION_CANCEL.equals(action)) { cancelDownload(); return START_NOT_STICKY; }
        if (ACTION_PAUSE.equals(action)) { pauseDownload(); return START_NOT_STICKY; }
        if (ACTION_RESUME.equals(action)) {
            if (!state.contains(EXTRA_URL)) { stopSelf(); return START_NOT_STICKY; }
            state.edit().putString("status", "running").apply();
            beginDownload();
            return START_REDELIVER_INTENT;
        }
        if (ACTION_START.equals(action) && intent != null) {
            if (running) {
                startForeground(PROGRESS_NOTIFICATION_ID, buildRunningNotification());
                return START_REDELIVER_INTENT;
            }
            String incomingUrl = safe(intent.getStringExtra(EXTRA_URL));
            String savedUrl = state.getString(EXTRA_URL, "");
            if ("running".equals(state.getString("status", ""))
                    && !savedUrl.isEmpty() && savedUrl.equals(incomingUrl)) {
                beginDownload();
                return START_REDELIVER_INTENT;
            }
            clearStateAndPartial();
            state.edit().putString(EXTRA_URL, incomingUrl)
                    .putString(EXTRA_FILENAME, safe(intent.getStringExtra(EXTRA_FILENAME)))
                    .putString(EXTRA_MIME, safe(intent.getStringExtra(EXTRA_MIME)))
                    .putString(EXTRA_USER_AGENT, safe(intent.getStringExtra(EXTRA_USER_AGENT)))
                    .putString(EXTRA_COOKIES, safe(intent.getStringExtra(EXTRA_COOKIES)))
                    .putString(EXTRA_REFERER, safe(intent.getStringExtra(EXTRA_REFERER)))
                    .putLong("downloaded", 0L).putLong("total", 0L)
                    .putString("status", "running").apply();
            if (state.getString(EXTRA_URL, "").isEmpty() || state.getString(EXTRA_FILENAME, "").isEmpty()) {
                showFailure("Download link or movie name is missing.");
                return START_NOT_STICKY;
            }
            beginDownload();
            return START_REDELIVER_INTENT;
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private synchronized void beginDownload() {
        if (running) return;
        pauseRequested.set(false);
        cancelRequested.set(false);
        running = true;
        startForeground(PROGRESS_NOTIFICATION_ID, buildRunningNotification());
        worker.execute(this::download);
    }

    private void download() {
        try {
            long downloaded = currentOutputSize();
            state.edit().putLong("downloaded", downloaded).apply();
            HttpURLConnection connection = (HttpURLConnection) new URL(state.getString(EXTRA_URL, "")).openConnection();
            activeConnection = connection;
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "video/*,application/octet-stream,*/*");
            setHeader(connection, "User-Agent", state.getString(EXTRA_USER_AGENT, ""));
            setHeader(connection, "Cookie", state.getString(EXTRA_COOKIES, ""));
            setHeader(connection, "Referer", state.getString(EXTRA_REFERER, ""));
            if (downloaded > 0L) connection.setRequestProperty("Range", "bytes=" + downloaded + "-");
            connection.connect();
            int response = connection.getResponseCode();
            if (response == 416) {
                long knownTotal = state.getLong("total", 0L);
                if (knownTotal > 0L && downloaded >= knownTotal) { finishSuccess(); return; }
                throw new IOException("The server rejected the saved download position");
            }
            if (response < 200 || response >= 300) throw new IOException("HTTP " + response);
            if (downloaded == 0L) improveFilenameFromResponse(connection);
            ensureDestination(state.getString(EXTRA_FILENAME, "movie.mp4"),
                    state.getString(EXTRA_MIME, "video/mp4"));
            boolean resumed = downloaded > 0L && response == HttpURLConnection.HTTP_PARTIAL;
            if (downloaded > 0L && !resumed) { truncateOutput(); downloaded = 0L; }
            long responseLength = connection.getContentLengthLong();
            long total = responseLength > 0L ? downloaded + responseLength : 0L;
            state.edit().putLong("downloaded", downloaded).putLong("total", total).apply();
            try (InputStream input = connection.getInputStream(); OutputHandle output = openOutput(downloaded)) {
                byte[] buffer = new byte[64 * 1024];
                long lastNotify = 0L;
                long written = downloaded;
                while (true) {
                    if (cancelRequested.get()) return;
                    if (pauseRequested.get()) {
                        state.edit().putLong("downloaded", written).putString("status", "paused").apply();
                        showPaused();
                        return;
                    }
                    int count = input.read(buffer);
                    if (count < 0) break;
                    output.write(buffer, 0, count);
                    written += count;
                    long now = System.currentTimeMillis();
                    if (now - lastNotify >= 650L) {
                        state.edit().putLong("downloaded", written).putLong("total", total).apply();
                        notifications.notify(PROGRESS_NOTIFICATION_ID, buildRunningNotification());
                        lastNotify = now;
                    }
                }
                state.edit().putLong("downloaded", written).putLong("total", total).apply();
            }
            finishSuccess();
        } catch (Exception error) {
            if (!pauseRequested.get() && !cancelRequested.get()) {
                Log.e(TAG, "Movie download failed", error);
                state.edit().putString("status", "failed").apply();
                showFailure("Download failed. Check your connection, then tap the movie to try again.");
            }
        } finally {
            HttpURLConnection connection = activeConnection;
            activeConnection = null;
            if (connection != null) connection.disconnect();
            running = false;
            boolean keepPausedNotification = "paused".equals(state.getString("status", ""));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(keepPausedNotification ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(!keepPausedNotification);
            }
            if (!keepPausedNotification) notifications.cancel(PROGRESS_NOTIFICATION_ID);
            stopSelf();
        }
    }

    private void pauseDownload() {
        pauseRequested.set(true);
        state.edit().putString("status", "paused").apply();
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        showPaused();
        if (!running) stopSelf();
    }

    private void cancelDownload() {
        cancelRequested.set(true);
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        deleteDestination();
        state.edit().clear().apply();
        notifications.cancel(PROGRESS_NOTIFICATION_ID);
        notifications.cancel(RESULT_NOTIFICATION_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    private void finishSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String uriString = state.getString("uri", "");
            if (!uriString.isEmpty()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(Uri.parse(uriString), values, null, null);
            }
        }
        String title = displayTitle(state.getString(EXTRA_FILENAME, "Movie"));
        state.edit().clear().apply();
        notifications.notify(RESULT_NOTIFICATION_ID, resultBuilder().setContentTitle(title + " downloaded")
                .setContentText("Saved in Downloads/MK Movies").setContentIntent(openDownloadsIntent())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setProgress(0, 0, false).setOngoing(false).setAutoCancel(true)
                .setOnlyAlertOnce(false).build());
    }

    private Notification buildRunningNotification() {
        String filename = state.getString(EXTRA_FILENAME, "Movie");
        long downloaded = state.getLong("downloaded", 0L);
        long total = state.getLong("total", 0L);
        NotificationCompat.Builder builder = progressBuilder()
                .setContentTitle("Downloading " + displayTitle(filename))
                .setContentText(formatProgress(downloaded, total)).setContentIntent(openDownloadsIntent())
                .setSubText("Pause or cancel below")
                .setOnlyAlertOnce(true).setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Pause", serviceAction(ACTION_PAUSE, 11))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", serviceAction(ACTION_CANCEL, 12));
        if (total > 0L) builder.setProgress(100, (int) Math.min(100L, downloaded * 100L / total), false);
        else builder.setProgress(0, 0, true);
        return builder.build();
    }

    private void showPaused() {
        String filename = state.getString(EXTRA_FILENAME, "Movie");
        Notification notification = progressBuilder().setContentTitle(displayTitle(filename) + " paused")
                .setContentText(formatProgress(state.getLong("downloaded", 0L), state.getLong("total", 0L)))
                .setContentIntent(openDownloadsIntent()).setOnlyAlertOnce(true).setOngoing(false)
                .addAction(android.R.drawable.ic_media_play, "Resume", serviceAction(ACTION_RESUME, 13))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", serviceAction(ACTION_CANCEL, 14)).build();
        notifications.notify(PROGRESS_NOTIFICATION_ID, notification);
    }

    private void showFailure(String message) {
        String filename = state.getString(EXTRA_FILENAME, "Movie");
        notifications.notify(RESULT_NOTIFICATION_ID, resultBuilder()
                .setContentTitle("Couldn’t download " + displayTitle(filename)).setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openAppIntent()).setCategory(NotificationCompat.CATEGORY_ERROR)
                .setOngoing(false).setAutoCancel(true).setOnlyAlertOnce(false).build());
    }

    private NotificationCompat.Builder progressBuilder() {
        return new NotificationCompat.Builder(this, PROGRESS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mk)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(new long[]{0L, 250L, 150L, 250L})
                .setSilent(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    }

    private NotificationCompat.Builder resultBuilder() {
        return new NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mk)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(new long[]{0L, 350L, 150L, 350L})
                .setSilent(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        return PendingIntent.getService(this, requestCode,
                new Intent(this, MovieDownloadService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openDownloadsIntent() {
        return PendingIntent.getActivity(this, 20, new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openAppIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 21, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void ensureDestination(String filename, String mime) throws IOException {
        if (!state.getString("uri", "").isEmpty() || !state.getString("path", "").isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime.isEmpty() ? "video/mp4" : mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MK Movies");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Android could not create the download file");
            state.edit().putString("uri", uri.toString()).apply();
        } else {
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MK Movies");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("Could not create Downloads/MK Movies");
            state.edit().putString("path", uniqueFile(folder, filename).getAbsolutePath()).apply();
        }
    }

    private OutputHandle openOutput(long position) throws IOException {
        String uriString = state.getString("uri", "");
        if (!uriString.isEmpty()) {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(Uri.parse(uriString), "rw");
            if (pfd == null) throw new IOException("Could not open Android download file");
            FileOutputStream stream = new FileOutputStream(pfd.getFileDescriptor());
            stream.getChannel().position(position);
            return new OutputHandle(stream, pfd);
        }
        RandomAccessFile file = new RandomAccessFile(state.getString("path", ""), "rw");
        file.seek(position);
        return new OutputHandle(file);
    }

    private long currentOutputSize() {
        try {
            String uriString = state.getString("uri", "");
            if (!uriString.isEmpty()) {
                try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(Uri.parse(uriString), "r")) {
                    return pfd == null ? 0L : pfd.getStatSize();
                }
            }
            File file = new File(state.getString("path", ""));
            return file.exists() ? file.length() : 0L;
        } catch (Exception ignored) { return state.getLong("downloaded", 0L); }
    }

    private void truncateOutput() throws IOException {
        try (OutputHandle output = openOutput(0L)) { output.truncate(); }
        state.edit().putLong("downloaded", 0L).apply();
    }

    private void clearStateAndPartial() {
        if (state.contains(EXTRA_URL)) deleteDestination();
        state.edit().clear().apply();
    }

    private void deleteDestination() {
        try {
            String uriString = state.getString("uri", "");
            if (!uriString.isEmpty()) getContentResolver().delete(Uri.parse(uriString), null, null);
            String path = state.getString("path", "");
            if (!path.isEmpty()) new File(path).delete();
        } catch (Exception error) { Log.w(TAG, "Could not delete partial movie", error); }
    }

    private static File uniqueFile(File folder, String filename) {
        File candidate = new File(folder, filename);
        if (!candidate.exists()) return candidate;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String ext = dot > 0 ? filename.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            candidate = new File(folder, base + " (" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(folder, base + "-" + System.currentTimeMillis() + ext);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        AudioAttributes soundAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel progressChannel = new NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "MK Movies download progress",
                NotificationManager.IMPORTANCE_HIGH
        );
        progressChannel.setDescription("Audible movie download progress with Pause, Resume and Cancel controls");
        progressChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, soundAttributes);
        progressChannel.enableVibration(true);
        progressChannel.setVibrationPattern(new long[]{0L, 250L, 150L, 250L});
        progressChannel.enableLights(true);
        progressChannel.setLightColor(0xFF1B5EAA);
        progressChannel.setShowBadge(true);
        notifications.createNotificationChannel(progressChannel);

        NotificationChannel resultChannel = new NotificationChannel(
                RESULT_CHANNEL_ID,
                "MK Movies download completed",
                NotificationManager.IMPORTANCE_HIGH
        );
        resultChannel.setDescription("Sound and vibration when an MK Movies download completes or fails");
        resultChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, soundAttributes);
        resultChannel.enableVibration(true);
        resultChannel.setVibrationPattern(new long[]{0L, 350L, 150L, 350L});
        resultChannel.enableLights(true);
        resultChannel.setLightColor(0xFF1B5EAA);
        resultChannel.setShowBadge(true);
        notifications.createNotificationChannel(resultChannel);
    }

    private static void setHeader(HttpURLConnection connection, String name, String value) {
        if (value != null && !value.isEmpty()) connection.setRequestProperty(name, value);
    }

    private void improveFilenameFromResponse(HttpURLConnection connection) {
        try {
            String current = state.getString(EXTRA_FILENAME, "");
            if (isUsefulFilename(current) && !looksGenerated(current)) return;
            String disposition = connection.getHeaderField("Content-Disposition");
            String contentType = connection.getContentType();
            String guessed = URLUtil.guessFileName(state.getString(EXTRA_URL, ""), disposition, contentType);
            if (isUsefulFilename(guessed) && !looksGenerated(guessed)) {
                state.edit().putString(EXTRA_FILENAME, sanitizeFilename(guessed)).apply();
            }
        } catch (Exception ignored) {
            // The page title supplied by MainActivity remains the primary filename source.
        }
    }

    private static boolean isUsefulFilename(String filename) {
        if (filename == null) return false;
        String name = filename.trim();
        return name.length() > 3
                && name.matches(".*[A-Za-z].*")
                && !name.toLowerCase(Locale.US).endsWith(".bin");
    }

    private static boolean looksGenerated(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.US)
                .replaceFirst("\\.(mp4|mkv|webm|m4v|mov|avi|3gp)$", "");
        return name.matches("^mk[- _]?movies?[- _]?\\d{8,}.*")
                || name.matches("^\\d{3,}[- _]\\d{6,}[- _].+")
                || name.matches("^[a-f0-9]{18,}$");
    }

    private static String sanitizeFilename(String filename) {
        String safe = filename == null ? "" : filename.replaceAll("[\\\\/:*?\"<>|]", "-")
                .replaceAll("\\s+", " ").trim();
        return safe.length() > 160 ? safe.substring(0, 160).trim() : safe;
    }

    private static String displayTitle(String filename) {
        if (filename == null || filename.trim().isEmpty()) return "Movie";
        String title = filename.trim();
        int dot = title.lastIndexOf('.');
        if (dot > 0) title = title.substring(0, dot);

        // R2/object-storage filenames may contain numeric IDs and a random key.
        // Keep the actual saved filename intact, but show only the human title
        // in Android's download notification.
        boolean generatedPrefix = title.matches("^\\d{3,}[-_]\\d{6,}[-_][A-Za-z0-9]{5,}[-_].+");
        if (generatedPrefix) {
            title = title.replaceFirst("^\\d{3,}[-_]\\d{6,}[-_][A-Za-z0-9]{5,}[-_]+", "");
            title = title.replaceFirst("[-_]\\d{3,}$", "");
        }

        title = title.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
        return title.isEmpty() ? "Movie" : title;
    }

    private static String formatProgress(long downloaded, long total) {
        if (total > 0L) return String.format(Locale.US, "%d%% • %s of %s",
                Math.min(100L, downloaded * 100L / total), formatBytes(downloaded), formatBytes(total));
        return downloaded > 0L ? formatBytes(downloaded) + " downloaded" : "Preparing download…";
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GB", bytes / (1024d * 1024d * 1024d));
        if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024d * 1024d));
        if (bytes >= 1024L) return String.format(Locale.US, "%.0f KB", bytes / 1024d);
        return bytes + " B";
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private static final class OutputHandle implements AutoCloseable {
        private final FileOutputStream stream;
        private final RandomAccessFile random;
        private final ParcelFileDescriptor descriptor;
        OutputHandle(FileOutputStream stream, ParcelFileDescriptor descriptor) {
            this.stream = stream; this.descriptor = descriptor; this.random = null;
        }
        OutputHandle(RandomAccessFile random) {
            this.stream = null; this.descriptor = null; this.random = random;
        }
        void write(byte[] bytes, int offset, int count) throws IOException {
            if (stream != null) stream.write(bytes, offset, count); else random.write(bytes, offset, count);
        }
        void truncate() throws IOException {
            if (stream != null) stream.getChannel().truncate(0L); else random.setLength(0L);
        }
        @Override public void close() throws IOException {
            if (stream != null) stream.close();
            if (random != null) random.close();
            if (descriptor != null) descriptor.close();
        }
    }
}
