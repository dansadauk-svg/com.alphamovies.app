package com.alphamovies.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class MkTokenRegistrar {
    private static final String TAG = "AlphaMoviesFCM";
    private static final String REGISTER_PATH = "wp-json/mkpush/v2/register-token";
    private static final String PREFS = "alpha_movies_fcm_registration";
    private static final String KEY_TOKEN = "last_token";
    private static final String KEY_VERSION = "last_version";
    private static final String KEY_SUCCESS_AT = "last_success_at";
    private static final String KEY_ATTEMPT_AT = "last_attempt_at";
    private static final long SUCCESS_REFRESH_MS = 24L * 60L * 60L * 1000L;
    private static final long RETRY_COOLDOWN_MS = 30L * 60L * 1000L;

    private MkTokenRegistrar() {}

    public static void registerTokenAsync(final Context context, final String token) {
        if (context == null || token == null || token.length() < 40) return;
        final Context appContext = context.getApplicationContext();
        if (!shouldRegister(appContext, token)) return;
        markAttempt(appContext);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = registerToken(token);
                if (ok) markSuccess(appContext, token);
            }
        }).start();
    }

    private static boolean shouldRegister(Context context, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String oldToken = prefs.getString(KEY_TOKEN, "");
        String oldVersion = prefs.getString(KEY_VERSION, "");
        long successAt = prefs.getLong(KEY_SUCCESS_AT, 0L);
        long attemptAt = prefs.getLong(KEY_ATTEMPT_AT, 0L);

        boolean sameToken = token.equals(oldToken);
        boolean sameVersion = BuildConfig.VERSION_NAME.equals(oldVersion);
        if (sameToken && sameVersion && successAt > 0 && (now - successAt) < SUCCESS_REFRESH_MS) {
            return false;
        }
        return (now - attemptAt) >= RETRY_COOLDOWN_MS;
    }

    private static void markAttempt(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_ATTEMPT_AT, System.currentTimeMillis())
                .apply();
    }

    private static void markSuccess(Context context, String token) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_VERSION, BuildConfig.VERSION_NAME)
                .putLong(KEY_SUCCESS_AT, System.currentTimeMillis())
                .apply();
    }

    private static boolean registerToken(String token) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("token", token);
            body.put("permission", "granted");
            body.put("source", "native-android");
            body.put("package", BuildConfig.APPLICATION_ID);
            body.put("app_package", BuildConfig.APPLICATION_ID);
            body.put("app_version", BuildConfig.VERSION_NAME);
            body.put("android_sdk", Build.VERSION.SDK_INT);
            body.put("device", Build.MANUFACTURER + " " + Build.MODEL);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            URL url = new URL(registerEndpoint());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "AlphaMoviesAndroid/" + BuildConfig.VERSION_NAME);

            OutputStream os = connection.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = connection.getResponseCode();
            String response = readStream(code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            Log.d(TAG, "Token register HTTP " + code + ": " + response);
            return code >= 200 && code < 300;
        } catch (Exception e) {
            Log.w(TAG, "Could not register native FCM token", e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String registerEndpoint() {
        String base = AppConfig.WEBSITE_URL;
        if (base.endsWith("/")) {
            return base + REGISTER_PATH;
        }
        return base + "/" + REGISTER_PATH;
    }

    private static String readStream(InputStream stream) {
        if (stream == null) return "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            reader.close();
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
