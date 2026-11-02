package com.alphamovies.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resumable foreground movie downloader.
 *
 * DownloadManager does not expose a Pause action. This service owns the HTTP
 * connection, uses byte-range requests when resuming and keeps a visible
 * Android notification with Pause/Resume and Cancel controls.
 */
public class MovieDownloadService extends Service {
    private static final String TAG = "AlphaMovieDownload";
    private static final String CHANNEL_ID = "alpha_movie_downloads_v2";
    private static final int NOTIFICATION_ID = 4701;
    private static final int COMPLETE_NOTIFICATION_ID = 4702;

    private static final String ACTION_START = "com.alphamovies.app.download.START";
    private static final String ACTION_PAUSE = "com.alphamovies.app.download.PAUSE";
    private static final String ACTION_RESUME = "com.alphamovies.app.download.RESUME";
    private static final String ACTION_CANCEL = "com.alphamovies.app.download.CANCEL";

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_FILENAME = "filename";
    private static final String EXTRA_MIME = "mime";
    private static final String EXTRA_USER_AGENT = "user_agent";
    private static final String EXTRA_COOKIES = "cookies";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean paused;
    private volatile boolean cancelled;
    private volatile boolean workerRunning;
    private volatile HttpURLConnection activeConnection;

    private String downloadUrl;
    private String filename;
    private String mimeType;
    private String userAgent;
    private String cookies;
    private Uri destinationUri;
    private File legacyFile;
    private long downloadedBytes;
    private long totalBytes = -1L;
    private int lastProgress = -1;
    private long lastNotificationAt;

    public static Intent newDownloadIntent(Context context, String url, String filename,
                                           String mimeType, String userAgent, String cookies) {
        return new Intent(context, MovieDownloadService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_FILENAME, filename)
                .putExtra(EXTRA_MIME, mimeType)
                .putExtra(EXTRA_USER_AGENT, userAgent)
                .putExtra(EXTRA_COOKIES, cookies);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            pauseDownload();
        } else if (ACTION_RESUME.equals(action)) {
            resumeDownload();
        } else if (ACTION_CANCEL.equals(action)) {
            cancelDownload();
        } else if (ACTION_START.equals(action)) {
            beginNewDownload(intent);
        }
        return START_NOT_STICKY;
    }

    private synchronized void beginNewDownload(Intent intent) {
        if (workerRunning || downloadUrl != null) {
            // Keep one reliable movie transfer at a time. The existing
            // notification already gives the user Pause and Cancel controls.
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(
                        "A movie is already downloading. Pause or cancel it first.",
                        progressPercent(), 100, totalBytes <= 0, paused));
            }
            return;
        }
        downloadUrl = safe(intent.getStringExtra(EXTRA_URL));
        filename = safeFilename(intent.getStringExtra(EXTRA_FILENAME));
        mimeType = safe(intent.getStringExtra(EXTRA_MIME));
        userAgent = safe(intent.getStringExtra(EXTRA_USER_AGENT));
        cookies = safe(intent.getStringExtra(EXTRA_COOKIES));
        paused = false;
        cancelled = false;
        downloadedBytes = 0L;
        totalBytes = -1L;
        destinationUri = null;
        legacyFile = null;
        lastProgress = -1;
        startForeground(NOTIFICATION_ID, buildNotification("Starting download…", 0, 0, false, false));
        startWorker();
    }

    private synchronized void startWorker() {
        if (workerRunning || paused || cancelled || downloadUrl == null || downloadUrl.isEmpty()) return;
        workerRunning = true;
        executor.execute(() -> {
            try {
                runDownload();
            } finally {
                workerRunning = false;
                // Resume may be tapped while the old socket is still closing.
                // Start the replacement worker after that cleanup completes.
                if (!paused && !cancelled && downloadUrl != null) startWorker();
            }
        });
    }

    private void runDownload() {
        ParcelFileDescriptor descriptor = null;
        OutputStream output = null;
        InputStream input = null;
        try {
            ensureDestination();
            long resumeAt = downloadedBytes;
            HttpURLConnection connection = openConnection(resumeAt);
            activeConnection = connection;
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK && resumeAt > 0) {
                // Server ignored Range. Restart cleanly to avoid a corrupted file.
                downloadedBytes = 0L;
                resumeAt = 0L;
            } else if (responseCode != HttpURLConnection.HTTP_OK
                    && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new IllegalStateException("Server returned HTTP " + responseCode);
            }

            long responseLength = parseContentLength(connection.getHeaderField("Content-Length"));
            totalBytes = responseLength > 0 ? resumeAt + responseLength : -1L;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                descriptor = getContentResolver().openFileDescriptor(destinationUri, "rw");
                if (descriptor == null) throw new IllegalStateException("Could not open download destination");
                FileOutputStream fileOutput = new FileOutputStream(descriptor.getFileDescriptor());
                FileChannel channel = fileOutput.getChannel();
                if (resumeAt == 0) channel.truncate(0);
                channel.position(resumeAt);
                output = fileOutput;
            } else {
                output = new FileOutputStream(legacyFile, resumeAt > 0);
                if (resumeAt == 0 && legacyFile.length() > 0) {
                    output.close();
                    output = new FileOutputStream(legacyFile, false);
                }
            }

            input = new BufferedInputStream(connection.getInputStream(), 64 * 1024);
            byte[] buffer = new byte[64 * 1024];
            int count;
            updateProgressNotification(true);
            while (!paused && !cancelled && (count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                downloadedBytes += count;
                updateProgressNotification(false);
            }
            output.flush();

            if (cancelled) {
                cancelCurrentFile();
                return;
            }
            if (paused) {
                showPausedNotification();
                return;
            }

            finishDestination();
            showCompletedNotification();
            clearJobState();
            stopForeground(true);
            stopSelf();
        } catch (Exception error) {
            Log.e(TAG, "Movie download failed", error);
            if (!paused && !cancelled) showFailedNotification(error.getMessage());
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            closeQuietly(descriptor);
            HttpURLConnection connection = activeConnection;
            activeConnection = null;
            if (connection != null) connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(long resumeAt) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", userAgent.isEmpty() ? "AlphaMoviesAndroid/" + BuildConfig.VERSION_NAME : userAgent);
        connection.setRequestProperty("Accept", "video/*,application/octet-stream,*/*");
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (!cookies.isEmpty()) connection.setRequestProperty("Cookie", cookies);
        if (resumeAt > 0) connection.setRequestProperty("Range", "bytes=" + resumeAt + "-");
        connection.connect();
        return connection;
    }

    private void ensureDestination() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (destinationUri != null) return;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType.isEmpty() ? "video/mp4" : mimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Alpha Movies");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            destinationUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (destinationUri == null) throw new IllegalStateException("Android could not create the download file");
        } else {
            if (legacyFile != null) return;
            File directory;
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Alpha Movies");
            } else {
                directory = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Alpha Movies");
            }
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create Downloads folder");
            legacyFile = uniqueFile(directory, filename);
        }
    }

    private void finishDestination() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && destinationUri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(destinationUri, values, null, null);
        } else if (legacyFile != null) {
            Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(legacyFile));
            sendBroadcast(scan);
        }
    }

    private synchronized void pauseDownload() {
        if (downloadUrl == null || cancelled) return;
        paused = true;
        disconnectActiveConnection();
        showPausedNotification();
    }

    private synchronized void resumeDownload() {
        if (downloadUrl == null || cancelled) return;
        paused = false;
        startForeground(NOTIFICATION_ID, buildNotification("Resuming…", progressPercent(), 100, true, false));
        startWorker();
    }

    private synchronized void cancelDownload() {
        cancelled = true;
        paused = false;
        disconnectActiveConnection();
        cancelCurrentFile();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        clearJobState();
        stopForeground(true);
        stopSelf();
    }

    private void cancelCurrentFile() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && destinationUri != null) {
                getContentResolver().delete(destinationUri, null, null);
            } else if (legacyFile != null && legacyFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                legacyFile.delete();
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not remove partial download", error);
        }
    }

    private void disconnectActiveConnection() {
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    private void updateProgressNotification(boolean force) {
        long now = System.currentTimeMillis();
        int progress = progressPercent();
        if (!force && progress == lastProgress && now - lastNotificationAt < 1000) return;
        if (!force && now - lastNotificationAt < 750) return;
        lastProgress = progress;
        lastNotificationAt = now;
        String status = totalBytes > 0
                ? humanBytes(downloadedBytes) + " of " + humanBytes(totalBytes)
                : humanBytes(downloadedBytes) + " downloaded";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID,
                buildNotification(status, progress, 100, totalBytes <= 0, false));
    }

    private void showPausedNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID,
                buildNotification("Paused • " + humanBytes(downloadedBytes) + " downloaded",
                        progressPercent(), 100, totalBytes <= 0, true));
    }

    private void showCompletedNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        Notification.Builder builder = notificationBuilder()
                .setSmallIcon(R.drawable.ic_stat_alpha)
                .setContentTitle(filename)
                .setContentText("Download complete • Tap to open Downloads")
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(openDownloadsIntent());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        manager.notify(COMPLETE_NOTIFICATION_ID, builder.build());
    }

    private void showFailedNotification(String reason) {
        paused = true;
        String text = "Download interrupted. Tap Resume to continue.";
        if (reason != null && reason.toLowerCase(Locale.US).contains("space")) text = "Not enough storage space.";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID,
                buildNotification(text, progressPercent(), 100, totalBytes <= 0, true));
    }

    private Notification buildNotification(String status, int progress, int max,
                                           boolean indeterminate, boolean isPaused) {
        Notification.Builder builder = notificationBuilder()
                .setSmallIcon(R.drawable.ic_stat_alpha)
                .setColor(Color.rgb(220, 38, 38))
                .setContentTitle(filename == null || filename.isEmpty() ? "Alpha Movies download" : filename)
                .setContentText(status)
                .setStyle(new Notification.BigTextStyle().bigText(status))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(max, Math.max(0, progress), indeterminate)
                .addAction(actionIcon(), isPaused ? "Resume" : "Pause",
                        serviceAction(isPaused ? ACTION_RESUME : ACTION_PAUSE, isPaused ? 4704 : 4703))
                .addAction(actionIcon(), "Cancel", serviceAction(ACTION_CANCEL, 4705));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        return builder.build();
    }

    private Notification.Builder notificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return new Notification.Builder(this, CHANNEL_ID);
        return new Notification.Builder(this);
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, MovieDownloadService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private PendingIntent openDownloadsIntent() {
        Intent intent = new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 4706, intent, flags);
    }

    private int actionIcon() {
        return R.drawable.ic_stat_alpha;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Movie downloads",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Audible movie download progress, pause, resume and completion alerts.");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 180, 100, 180});
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
        manager.createNotificationChannel(channel);
    }

    private int progressPercent() {
        if (totalBytes <= 0) return 0;
        return (int) Math.min(100L, (downloadedBytes * 100L) / totalBytes);
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private static long parseContentLength(String value) {
        if (value == null || value.trim().isEmpty()) return -1L;
        try { return Long.parseLong(value.trim()); } catch (NumberFormatException ignored) { return -1L; }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeFilename(String value) {
        String result = safe(value).replaceAll("[\\\\/:*?\"<>|]", "-");
        if (result.isEmpty()) result = "Alpha-Movies-" + System.currentTimeMillis() + ".mp4";
        if (!result.contains(".")) result += ".mp4";
        return result;
    }

    private static File uniqueFile(File directory, String name) {
        File file = new File(directory, name);
        if (!file.exists()) return file;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int index = 2;
        while (file.exists()) file = new File(directory, base + " (" + index++ + ")" + ext);
        return file;
    }

    private void clearJobState() {
        downloadUrl = null;
        destinationUri = null;
        legacyFile = null;
        downloadedBytes = 0L;
        totalBytes = -1L;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        cancelled = true;
        disconnectActiveConnection();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
