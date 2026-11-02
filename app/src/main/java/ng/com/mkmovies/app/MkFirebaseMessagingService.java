package ng.com.mkmovies.app;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class MkFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MKMoviesFCMService";
    public static final String CHANNEL_ID = "mk_movies_alerts_v3";
    public static final String ALL_USERS_TOPIC = "mk_movies_all";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        FirebaseMessaging.getInstance().subscribeToTopic(ALL_USERS_TOPIC)
                .addOnFailureListener(error -> Log.w(TAG, "Could not restore all-user topic subscription", error));
        MkTokenRegistrar.registerTokenAsync(getApplicationContext(), token, notificationPermission());
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        FirebaseMessaging.getInstance().subscribeToTopic(ALL_USERS_TOPIC)
                .addOnFailureListener(error -> Log.w(TAG, "Could not refresh all-user topic subscription", error));
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
                MkTokenRegistrar.registerTokenAsync(getApplicationContext(), token, notificationPermission())
        );
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String, String> data = message.getData();

        String title = value(data, "title", "MK Movies");
        String body = value(data, "body", "New update available");
        String url = value(data, "url", "https://mkmovies.com.ng/");
        String imageUrl = firstValue(data,
                "image", "image_url", "imageUrl", "poster", "poster_url",
                "movie_poster", "movie_poster_url", "big_picture", "bigPicture",
                "large_image", "thumbnail", "thumbnail_url");

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
            if (message.getNotification().getLink() != null) {
                url = message.getNotification().getLink().toString();
            }
            if ((imageUrl == null || imageUrl.trim().isEmpty()) && message.getNotification().getImageUrl() != null) {
                imageUrl = message.getNotification().getImageUrl().toString();
            }
        }

        final int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        showNotification(title, body, url, null, notificationId);

        if (imageUrl != null && imageUrl.startsWith("http")) {
            final String finalTitle = title;
            final String finalBody = body;
            final String finalUrl = url;
            final String finalImageUrl = imageUrl;
            new Thread(() -> {
                Bitmap bigPicture = loadBitmap(finalImageUrl);
                if (bigPicture != null) {
                    showNotification(finalTitle, finalBody, finalUrl, bigPicture, notificationId);
                }
            }, "MKMoviesNotificationImage").start();
        }
    }

    private String value(Map<String, String> data, String key, String fallback) {
        String v = data != null ? data.get(key) : null;
        return (v == null || v.trim().isEmpty()) ? fallback : v;
    }

    private String firstValue(Map<String, String> data, String... keys) {
        if (data == null || keys == null) return "";
        for (String key : keys) {
            String v = data.get(key);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private Bitmap loadBitmap(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("http")) return null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            return BitmapFactory.decodeStream(connection.getInputStream());
        } catch (Exception e) {
            Log.w(TAG, "Could not load notification image", e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String notificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return "denied";
        }
        return NotificationManagerCompat.from(this).areNotificationsEnabled() ? "granted" : "denied";
    }

    private void showNotification(String title, String body, String url, Bitmap bigPicture, int notificationId) {
        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted; cannot show notification.");
            return;
        }

        String openUrl = (url == null || url.trim().isEmpty()) ? "https://mkmovies.com.ng/" : url.trim();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(openUrl));
        intent.putExtra("url", openUrl);
        intent.putExtra("open_url", openUrl);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mk)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_PROMO)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (bigPicture != null) {
            builder.setLargeIcon(bigPicture)
                    .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bigPicture).setSummaryText(body));
        } else {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }

        NotificationManagerCompat.from(this).notify(notificationId, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MK Movies Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("New movies, new episodes and account alerts from MK Movies.");
            channel.enableVibration(true);
            channel.setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            );
            manager.createNotificationChannel(channel);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NotificationChannel existingLegacyChannel = manager.getNotificationChannel("movie_updates");
                if (existingLegacyChannel != null
                        && existingLegacyChannel.getImportance() < NotificationManager.IMPORTANCE_DEFAULT
                        && !existingLegacyChannel.hasUserSetImportance()) {
                    manager.deleteNotificationChannel("movie_updates");
                }
            }
            NotificationChannel legacySenderChannel = new NotificationChannel(
                    "movie_updates",
                    "Movie Updates",
                    NotificationManager.IMPORTANCE_HIGH
            );
            legacySenderChannel.setDescription("New movie and episode notifications.");
            legacySenderChannel.enableVibration(true);
            legacySenderChannel.setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            );
            manager.createNotificationChannel(legacySenderChannel);
        }
    }
}
