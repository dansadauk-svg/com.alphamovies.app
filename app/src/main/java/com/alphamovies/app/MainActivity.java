package com.alphamovies.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "AlphaMoviesMain";
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final int REQ_POST_NOTIFICATIONS = 701;
    private static final String NOTIFICATION_CHANNEL_ID = "movie_updates";
    private static final int EDGE_SWIPE_WIDTH_DP = 56;
    private static final int BACK_SWIPE_DISTANCE_DP = 96;
    private static final int REFRESH_SWIPE_DISTANCE_DP = 130;
    private static final int SWIPE_MAX_OFF_AXIS_DP = 90;

    private WebView webView;
    private LinearLayout splashLayout;
    private LinearLayout offlineLayout;
    private LinearLayout refreshIndicator;
    private ProgressBar splashProgress;
    private TextView progressText;
    private FrameLayout customViewContainer;
    private AlphaWebChromeClient alphaWebChromeClient;

    private ValueCallback<Uri[]> filePathCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean firstPageLoaded = false;
    private long lastBackPressedTime = 0;
    private float gestureStartX = 0f;
    private float gestureStartY = 0f;
    private boolean gestureStartedAtTop = false;
    private int originalSystemUiVisibility = 0;
    private int originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindowForSafeHeader();
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        splashLayout = findViewById(R.id.splashLayout);
        offlineLayout = findViewById(R.id.offlineLayout);
        refreshIndicator = findViewById(R.id.refreshIndicator);
        splashProgress = findViewById(R.id.splashProgress);
        progressText = findViewById(R.id.progressText);
        customViewContainer = findViewById(R.id.customViewContainer);
        Button retryButton = findViewById(R.id.retryButton);

        setupWebView();
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        registerNativeToken();

        retryButton.setOnClickListener(v -> loadHome());
        String launchUrl = getLaunchUrl(getIntent());
        if (launchUrl != null) {
            loadUrl(launchUrl);
        } else {
            loadHome();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String launchUrl = getLaunchUrl(intent);
        if (launchUrl != null && webView != null) {
            loadUrl(launchUrl);
        }
    }

    private void configureWindowForSafeHeader() {
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Do not draw the WebView behind the status bar or camera cutout.
        // This keeps the website header below punch-hole/notch areas.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            window.setAttributes(attributes);
        }
    }

    private void setVideoCutoutMode(boolean fullscreen) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = fullscreen
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            getWindow().setAttributes(attributes);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.setBackgroundColor(Color.BLACK);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setTextZoom(100);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " AlphaMoviesAndroid/" + BuildConfig.VERSION_NAME);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new AlphaWebViewClient());
        alphaWebChromeClient = new AlphaWebChromeClient();
        webView.setWebChromeClient(alphaWebChromeClient);
        webView.addJavascriptInterface(new AlphaDownloadBridge(), "AlphaMoviesAndroidBridge");
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                startDownload(url, userAgent, contentDisposition, mimeType);
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handleWebViewGesture(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean handleWebViewGesture(MotionEvent event) {
        if (webView == null
                || webView.getVisibility() != View.VISIBLE
                || customView != null
                || splashLayout.getVisibility() == View.VISIBLE
                || offlineLayout.getVisibility() == View.VISIBLE) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureStartX = event.getX();
                gestureStartY = event.getY();
                gestureStartedAtTop = webView.getScrollY() <= 0;
                return false;
            case MotionEvent.ACTION_UP:
                return handleCompletedGesture(event);
            case MotionEvent.ACTION_CANCEL:
                gestureStartX = 0f;
                gestureStartY = 0f;
                gestureStartedAtTop = false;
                return false;
            default:
                return false;
        }
    }

    private boolean handleCompletedGesture(MotionEvent event) {
        float dx = event.getX() - gestureStartX;
        float dy = event.getY() - gestureStartY;
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        int edgeWidth = dpToPx(EDGE_SWIPE_WIDTH_DP);
        int backDistance = dpToPx(BACK_SWIPE_DISTANCE_DP);
        int refreshDistance = dpToPx(REFRESH_SWIPE_DISTANCE_DP);
        int maxOffAxis = dpToPx(SWIPE_MAX_OFF_AXIS_DP);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        boolean startedFromLeftEdge = gestureStartX <= edgeWidth;
        boolean startedFromRightEdge = gestureStartX >= screenWidth - edgeWidth;
        boolean swipedTowardCenter = (startedFromLeftEdge && dx > backDistance)
                || (startedFromRightEdge && dx < -backDistance);

        if (swipedTowardCenter && absDy < maxOffAxis) {
            handleBackSwipe();
            return true;
        }

        if (gestureStartedAtTop && dy > refreshDistance && absDx < maxOffAxis) {
            refreshCurrentPage();
            return true;
        }

        return false;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void loadHome() {
        loadUrl(AppConfig.WEBSITE_URL);
    }

    private void loadUrl(String url) {
        if (!isNetworkAvailable()) {
            showOffline();
            return;
        }
        offlineLayout.setVisibility(View.GONE);
        if (!firstPageLoaded) {
            showSplash(0);
        }
        String safeUrl = isHttpUrl(url) ? url.trim() : AppConfig.WEBSITE_URL;
        webView.loadUrl(safeUrl);
    }

    private void refreshCurrentPage() {
        if (!isNetworkAvailable()) {
            showOffline();
            return;
        }

        offlineLayout.setVisibility(View.GONE);
        showRefreshIndicator();
        webView.clearCache(true);
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.trim().isEmpty()) {
            webView.loadUrl(AppConfig.WEBSITE_URL);
        } else {
            webView.reload();
        }
    }

    private String getLaunchUrl(Intent intent) {
        if (intent == null) return null;
        String url = intent.getStringExtra("url");
        if (!isHttpUrl(url)) {
            url = intent.getStringExtra("open_url");
        }
        if (!isHttpUrl(url) && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            url = intent.getData().toString();
        }
        return isHttpUrl(url) ? url.trim() : null;
    }

    private boolean isHttpUrl(String url) {
        if (url == null) return false;
        String trimmed = url.trim().toLowerCase();
        return trimmed.startsWith("https://") || trimmed.startsWith("http://");
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            registerNativeToken();
        }
    }

    private void registerNativeToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                MkTokenRegistrar.registerTokenAsync(getApplicationContext(), task.getResult());
            } else {
                Log.w(TAG, "Could not get native FCM token", task.getException());
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Alpha Movies Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("New movies, new episodes and account alerts from Alpha Movies.");
            manager.createNotificationChannel(channel);
        }
    }

    private void showRefreshIndicator() {
        if (refreshIndicator == null) return;
        refreshIndicator.setVisibility(View.VISIBLE);
        refreshIndicator.bringToFront();
    }

    private void hideRefreshIndicator() {
        if (refreshIndicator == null) return;
        refreshIndicator.setVisibility(View.GONE);
    }

    private void showSplash(int progress) {
        splashLayout.setVisibility(View.VISIBLE);
        splashLayout.setAlpha(1f);
        updateSplashProgress(progress);
    }

    private void updateSplashProgress(int progress) {
        int safeProgress = Math.max(0, Math.min(100, progress));
        splashProgress.setProgress(safeProgress);
        progressText.setText(safeProgress + "%");
    }

    private void hideSplashWhenReady() {
        if (firstPageLoaded) return;
        firstPageLoaded = true;
        updateSplashProgress(100);
        splashLayout.animate()
                .alpha(0f)
                .setDuration(280)
                .withEndAction(() -> splashLayout.setVisibility(View.GONE))
                .start();
    }

    private void showOffline() {
        hideRefreshIndicator();
        splashLayout.setVisibility(View.GONE);
        offlineLayout.setVisibility(View.VISIBLE);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    private boolean openWithExternalApp(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return true;
        }
    }

    private boolean shouldOpenExternally(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.startsWith("tel:")
                || lowerUrl.startsWith("mailto:")
                || lowerUrl.startsWith("sms:")
                || lowerUrl.startsWith("whatsapp:")
                || lowerUrl.startsWith("intent:")
                || lowerUrl.startsWith("market:");
    }

    private class AlphaDownloadBridge {
        @JavascriptInterface
        public void startMovieDownload(final String url) {
            if (url == null || url.trim().isEmpty()) return;
            runOnUiThread(() -> startDownload(
                    url,
                    webView != null ? webView.getSettings().getUserAgentString() : "AlphaMoviesAndroid/" + BuildConfig.VERSION_NAME,
                    "attachment",
                    guessMimeTypeFromUrl(url)
            ));
        }
    }

    private void injectDownloadClickInterceptor(WebView view) {
        if (view == null) return;
        String js = "(function(){"
                + "if(window.__alpha_movies_download_interceptor_v1)return;"
                + "window.__alpha_movies_download_interceptor_v1=true;"
                + "function isDownloadLink(a){"
                + "if(!a||!a.href)return false;"
                + "var h=String(a.href).toLowerCase();"
                + "var c=(a.className||'').toString();"
                + "return a.hasAttribute('download')"
                + "|| c.indexOf('mk-watch-white__download')!==-1"
                + "|| c.indexOf('mkmc-watch-download')!==-1"
                + "|| c.indexOf('mkmc-download-button')!==-1"
                + "|| c.indexOf('mkmc-theme-download-button')!==-1"
                + "|| c.indexOf('mk-download-button')!==-1"
                + "|| c.indexOf('download-movie-button')!==-1"
                + "|| h.indexOf('download')!==-1"
                + "|| h.indexOf('response-content-disposition=')!==-1;"
                + "}"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target&&e.target.closest?e.target.closest('a'):null;"
                + "if(!isDownloadLink(a))return;"
                + "e.preventDefault();e.stopPropagation();"
                + "try{window.AlphaMoviesAndroidBridge.startMovieDownload(a.href);}catch(err){window.location.href=a.href;}"
                + "},true);"
                + "})();";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(js, null);
        } else {
            view.loadUrl("javascript:" + js);
        }
    }

    private boolean isMovieDownloadUrl(Uri uri) {
        if (uri == null) return false;
        String url = uri.toString().toLowerCase(Locale.US);
        return url.contains("download")
                || url.contains("response-content-disposition=")
                || url.contains("mk_download_link")
                || url.contains("mkmc_download_movie")
                || url.contains("action=mkmc_download_movie");
    }

    private boolean isLikelyMovieFileUrl(Uri uri) {
        if (uri == null) return false;
        String url = uri.toString().toLowerCase(Locale.US);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.US);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);

        boolean movieExtension = path.endsWith(".mp4")
                || path.endsWith(".mkv")
                || path.endsWith(".avi")
                || path.endsWith(".mov")
                || path.endsWith(".webm")
                || path.endsWith(".m4v")
                || path.endsWith(".3gp");

        boolean storageHost = host.contains("r2.dev")
                || host.contains("r2.cloudflarestorage.com")
                || host.contains("cloudflare")
                || host.contains("cdn")
                || host.contains("storage")
                || host.contains("amazonaws.com");

        return movieExtension || (storageHost && (url.contains(".mp4") || url.contains(".mkv") || url.contains(".webm") || url.contains("download")));
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;

        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (isMovieDownloadUrl(uri) || isLikelyMovieFileUrl(uri)) {
                startDownload(
                        uri.toString(),
                        webView != null ? webView.getSettings().getUserAgentString() : "AlphaMoviesAndroid/" + BuildConfig.VERSION_NAME,
                        "attachment",
                        guessMimeTypeFromUrl(uri.toString())
                );
                return true;
            }
            return false;
        }

        if (shouldOpenExternally(uri.toString())) {
            return openWithExternalApp(uri.toString());
        }
        return false;
    }

    private String guessMimeTypeFromUrl(String url) {
        if (url == null) return "application/octet-stream";
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".mp4")) return "video/mp4";
        if (lower.contains(".mkv")) return "video/x-matroska";
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".m4v")) return "video/x-m4v";
        if (lower.contains(".mov")) return "video/quicktime";
        if (lower.contains(".avi")) return "video/x-msvideo";
        if (lower.contains(".3gp")) return "video/3gpp";
        return "application/octet-stream";
    }

    private String cleanDownloadFilename(String url, String contentDisposition, String mimeType) {
        String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
        if (filename != null) filename = filename.trim();

        if (filename == null
                || filename.isEmpty()
                || filename.equalsIgnoreCase("admin-post.php")
                || filename.equalsIgnoreCase("download")
                || filename.toLowerCase(Locale.US).endsWith(".bin")) {
            try {
                Uri uri = Uri.parse(url);
                String downloadName = uri.getQueryParameter("download");
                if (downloadName != null && downloadName.trim().length() > 2) {
                    filename = downloadName.trim();
                }
            } catch (Exception ignored) {}
        }

        if (filename == null || filename.trim().isEmpty() || filename.equalsIgnoreCase("admin-post.php")) {
            String ext = ".mp4";
            String safeUrl = url == null ? "" : url.toLowerCase(Locale.US);
            if (safeUrl.contains(".mkv")) ext = ".mkv";
            else if (safeUrl.contains(".webm")) ext = ".webm";
            else if (safeUrl.contains(".m4v")) ext = ".m4v";
            else if (safeUrl.contains(".mov")) ext = ".mov";
            else if (safeUrl.contains(".avi")) ext = ".avi";
            else if (safeUrl.contains(".3gp")) ext = ".3gp";
            filename = "Alpha-Movies-" + System.currentTimeMillis() + ext;
        }

        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "-");
        if (!filename.contains(".")) filename = filename + ".mp4";
        return filename;
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            if (url == null || url.trim().isEmpty()) {
                Toast.makeText(this, "Download link is empty", Toast.LENGTH_SHORT).show();
                return;
            }

            String safeMimeType = (mimeType == null || mimeType.trim().isEmpty()) ? guessMimeTypeFromUrl(url) : mimeType;
            if (safeMimeType == null || safeMimeType.trim().isEmpty()) safeMimeType = "application/octet-stream";

            String filename = cleanDownloadFilename(url, contentDisposition, safeMimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(safeMimeType);
            request.addRequestHeader("User-Agent", userAgent != null ? userAgent : ("AlphaMoviesAndroid/" + BuildConfig.VERSION_NAME));
            request.addRequestHeader("Accept", "video/*,application/octet-stream,*/*");

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies == null) {
                cookies = CookieManager.getInstance().getCookie(AppConfig.WEBSITE_URL);
            }
            if (cookies != null && !cookies.trim().isEmpty()) {
                request.addRequestHeader("Cookie", cookies);
            }

            request.setTitle(filename);
            request.setDescription("Downloading movie from Alpha Movies");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.allowScanningByMediaScanner();

            try {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            } catch (Exception e) {
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);
            }

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Download started. Check your Downloads.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Download manager is not available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.w(TAG, "Movie download failed", e);
            Toast.makeText(this, "Download failed. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private class AlphaWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(Uri.parse(url));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Uri uri = request.getUrl();
                if (request.isForMainFrame()) {
                    return handleUrl(uri);
                }
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            CookieManager.getInstance().flush();
            injectDownloadClickInterceptor(view);
            hideRefreshIndicator();
            hideSplashWhenReady();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame() && !isNetworkAvailable()) {
                hideRefreshIndicator();
                showOffline();
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            if (!isNetworkAvailable()) {
                hideRefreshIndicator();
                showOffline();
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Production-safe behavior: never bypass SSL errors.
            handler.cancel();
            hideRefreshIndicator();
            showOffline();
        }
    }

    private class AlphaWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (!firstPageLoaded && splashLayout.getVisibility() == View.VISIBLE) {
                updateSplashProgress(newProgress);
            }
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;

            Intent intent;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Exception e) {
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
            }

            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                Toast.makeText(MainActivity.this, "No file picker found", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            enterFullscreenVideoMode();
            webView.setVisibility(View.GONE);
            customViewContainer.setVisibility(View.VISIBLE);
            customViewContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            customViewContainer.bringToFront();
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            customViewContainer.removeView(customView);
            customViewContainer.setVisibility(View.GONE);
            customView = null;
            webView.setVisibility(View.VISIBLE);
            exitFullscreenVideoMode();
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
        }
    }

    private void enterFullscreenVideoMode() {
        originalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        originalOrientation = getRequestedOrientation();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setVideoCutoutMode(true);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void exitFullscreenVideoMode() {
        getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setVideoCutoutMode(false);
        setRequestedOrientation(originalOrientation);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return;
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (navigateBackInsideApp()) return;

        long now = System.currentTimeMillis();
        if (now - lastBackPressedTime < 1800) {
            finish();
        } else {
            lastBackPressedTime = now;
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleBackSwipe() {
        if (navigateBackInsideApp()) return;
        Toast.makeText(this, "No previous page", Toast.LENGTH_SHORT).show();
    }

    private boolean navigateBackInsideApp() {
        if (customView != null) {
            alphaWebChromeClient.onHideCustomView();
            return true;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }

        if (webView != null && !isHomeUrl(webView.getUrl())) {
            webView.loadUrl(AppConfig.WEBSITE_URL);
            return true;
        }

        return false;
    }

    private boolean isHomeUrl(String url) {
        if (url == null || url.trim().isEmpty()) return true;
        try {
            Uri currentUri = Uri.parse(url);
            Uri homeUri = Uri.parse(AppConfig.WEBSITE_URL);
            String currentHost = currentUri.getHost();
            String homeHost = homeUri.getHost();
            String currentPath = currentUri.getPath();

            if (currentHost == null || homeHost == null || !currentHost.equalsIgnoreCase(homeHost)) {
                return false;
            }

            return currentPath == null || currentPath.isEmpty() || "/".equals(currentPath);
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (customView != null && alphaWebChromeClient != null) {
            alphaWebChromeClient.onHideCustomView();
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
