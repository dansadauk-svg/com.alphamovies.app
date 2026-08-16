package com.alphamovies.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class MkFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "AlphaMoviesFCMService";
    private static final String CHANNEL_ID = "movie_updates";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        MkTokenRegistrar.registerTokenAsync(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String, String> data = message.getData();

        String title = value(data, "title", "Alpha Movies");
        String body = value(data, "body", "New update available");
        String url = firstValue(data, "url", "open_url", "link", "click_url");
        String imageUrl = firstValue(data,
                "image", "image_url", "imageUrl", "poster", "poster_url",
                "movie_poster", "movie_poster_url", "big_picture", "bigPicture",
                "large_image", "thumbnail", "thumbnail_url");

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
            if ((imageUrl == null || imageUrl.trim().isEmpty()) && message.getNotification().getImageUrl() != null) {
                imageUrl = message.getNotification().getImageUrl().toString();
            }
        }

        showNotification(title, body, url, imageUrl);
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

    private void showNotification(String title, String body, String url, String imageUrl) {
        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted; cannot show notification.");
            return;
        }

        String openUrl = (url == null || url.trim().isEmpty()) ? AppConfig.WEBSITE_URL : url.trim();
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

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_alpha)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(Color.BLACK);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        Bitmap bigPicture = loadBitmap(imageUrl);
        if (bigPicture != null) {
            builder.setLargeIcon(bigPicture)
                    .setStyle(new Notification.BigPictureStyle()
                            .bigPicture(bigPicture)
                            .setSummaryText(body));
        } else {
            builder.setStyle(new Notification.BigTextStyle().bigText(body));
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alpha Movies Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("New movies, new episodes and account alerts from Alpha Movies.");
            manager.createNotificationChannel(channel);
        }
    }
}
