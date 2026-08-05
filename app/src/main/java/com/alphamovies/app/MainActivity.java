package com.alphamovies.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
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

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final int EDGE_SWIPE_WIDTH_DP = 56;
    private static final int BACK_SWIPE_DISTANCE_DP = 96;
    private static final int REFRESH_SWIPE_DISTANCE_DP = 130;
    private static final int SWIPE_MAX_OFF_AXIS_DP = 90;

    private WebView webView;
    private LinearLayout splashLayout;
    private LinearLayout offlineLayout;
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
        splashProgress = findViewById(R.id.splashProgress);
        progressText = findViewById(R.id.progressText);
        customViewContainer = findViewById(R.id.customViewContainer);
        Button retryButton = findViewById(R.id.retryButton);

        setupWebView();
        retryButton.setOnClickListener(v -> loadHome());
        loadHome();
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

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new AlphaWebViewClient());
        alphaWebChromeClient = new AlphaWebChromeClient();
        webView.setWebChromeClient(alphaWebChromeClient);
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
        if (!isNetworkAvailable()) {
            showOffline();
            return;
        }
        offlineLayout.setVisibility(View.GONE);
        if (!firstPageLoaded) {
            showSplash(0);
        }
        webView.loadUrl(AppConfig.WEBSITE_URL);
    }

    private void refreshCurrentPage() {
        if (!isNetworkAvailable()) {
            showOffline();
            return;
        }

        offlineLayout.setVisibility(View.GONE);
        Toast.makeText(this, "Refreshing", Toast.LENGTH_SHORT).show();
        webView.clearCache(true);
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.trim().isEmpty()) {
            webView.loadUrl(AppConfig.WEBSITE_URL);
        } else {
            webView.reload();
        }
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

    private class AlphaWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (shouldOpenExternally(url)) {
                return openWithExternalApp(url);
            }
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Uri uri = request.getUrl();
                String url = uri != null ? uri.toString() : null;
                if (request.isForMainFrame() && shouldOpenExternally(url)) {
                    return openWithExternalApp(url);
                }
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            CookieManager.getInstance().flush();
            hideSplashWhenReady();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame() && !isNetworkAvailable()) {
                showOffline();
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            if (!isNetworkAvailable()) {
                showOffline();
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Production-safe behavior: never bypass SSL errors.
            handler.cancel();
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
