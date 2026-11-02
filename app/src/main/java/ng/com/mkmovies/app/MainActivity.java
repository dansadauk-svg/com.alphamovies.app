package ng.com.mkmovies.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MKMoviesMain";
    private static final int REQ_POST_NOTIFICATIONS = 701;
    private static final int REQ_FILE_CHOOSER = 702;
    private static final String HOME_URL = "https://mkmovies.com.ng/?source=android_app";

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ValueCallback<Uri[]> filePathCallback;
    private ProgressBar topProgressBar;
    private ProgressBar splashProgressBar;
    private FrameLayout splashOverlay;
    private FrameLayout rootLayout;
    private View customFullscreenView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int originalSystemUiVisibility;
    private int originalOrientation;
    private boolean firstPageLoaded = false;
    private boolean offlineToastShown = false;
    private String lastKnownMovieTitle = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        createNotificationChannel();
        setupWebView();
        requestNotificationPermissionIfNeeded();
        registerNativeToken();

        String url = getLaunchUrl();
        webView.loadUrl(url);
    }

    private String getLaunchUrl() {
        Intent intent = getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null && ("https".equals(data.getScheme()) || "http".equals(data.getScheme()))) {
                return data.toString();
            }
            String extra = intent.getStringExtra("url");
            if (extra != null && extra.startsWith("http")) return extra;
            extra = intent.getStringExtra("open_url");
            if (extra != null && extra.startsWith("http")) return extra;
            extra = intent.getStringExtra("gcm.notification.link");
            if (extra != null && extra.startsWith("http")) return extra;
        }
        return HOME_URL;
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void applySafeAreaForCameraCutout(View root) {
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                int left = insets.getSystemWindowInsetLeft();
                int right = insets.getSystemWindowInsetRight();
                view.setPadding(left, top, right, bottom);
                return insets;
            });
            root.post(root::requestApplyInsets);
        }
    }

    private void setupWebView() {
        FrameLayout root = new FrameLayout(this);
        rootLayout = root;
        root.setBackgroundColor(Color.WHITE);
        applySafeAreaForCameraCutout(root);

        swipeRefreshLayout = new SwipeRefreshLayout(this);
        webView = new WebView(this);
        swipeRefreshLayout.addView(webView, new SwipeRefreshLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(swipeRefreshLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        topProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        topProgressBar.setMax(100);
        topProgressBar.setProgress(0);
        topProgressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        topParams.gravity = Gravity.TOP;
        root.addView(topProgressBar, topParams);

        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams splashParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        LinearLayout splashContent = new LinearLayout(this);
        splashContent.setOrientation(LinearLayout.VERTICAL);
        splashContent.setGravity(Gravity.CENTER);
        splashContent.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_splash);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        logoParams.width = dp(280);
        logoParams.bottomMargin = dp(28);
        splashContent.addView(logo, logoParams);

        splashProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        splashProgressBar.setMax(100);
        splashProgressBar.setProgress(8);
        splashProgressBar.setIndeterminate(false);
        LinearLayout.LayoutParams splashBarParams = new LinearLayout.LayoutParams(
                dp(220),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        splashContent.addView(splashProgressBar, splashBarParams);

        FrameLayout.LayoutParams splashContentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        splashOverlay.addView(splashContent, splashContentParams);
        root.addView(splashOverlay, splashParams);

        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        updateCacheMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua + " MKMoviesAndroid/" + BuildConfig.VERSION_NAME);

        webView.setBackgroundColor(Color.WHITE);
        webView.addJavascriptInterface(new MovieDownloadBridge(), "MKMoviesAndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null) {
                    return handleUrl(request.getUrl());
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                lastKnownMovieTitle = "";
                updateCacheMode();
                topProgressBar.setVisibility(View.VISIBLE);
                if (!firstPageLoaded) {
                    splashOverlay.setVisibility(View.VISIBLE);
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefreshLayout.setRefreshing(false);
                captureCurrentMovieTitle(view);
                injectDownloadClickInterceptor(view);
                maybeHideSplash();
                super.onPageFinished(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                topProgressBar.setProgress(newProgress);
                splashProgressBar.setProgress(Math.max(8, newProgress));
                if (newProgress >= 100) {
                    topProgressBar.setVisibility(View.GONE);
                    maybeHideSplash();
                } else {
                    topProgressBar.setVisibility(View.VISIBLE);
                }
                super.onProgressChanged(view, newProgress);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customFullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                customFullscreenView = view;
                customViewCallback = callback;
                originalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
                originalOrientation = getRequestedOrientation();

                swipeRefreshLayout.setVisibility(View.GONE);
                topProgressBar.setVisibility(View.GONE);
                splashOverlay.setVisibility(View.GONE);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                params.gravity = Gravity.CENTER;
                rootLayout.addView(customFullscreenView, params);
                rootLayout.setBackgroundColor(Color.BLACK);

                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );

                try {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                } catch (Exception ignored) {}
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenCustomView();
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open file picker", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                startDownloadWithResolvedTitle(url, userAgent, contentDisposition, mimeType);
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());
    }

    private void hideFullscreenCustomView() {
        if (customFullscreenView == null) return;

        try {
            rootLayout.removeView(customFullscreenView);
        } catch (Exception ignored) {}

        customFullscreenView = null;
        rootLayout.setBackgroundColor(Color.WHITE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);

        getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
        try {
            setRequestedOrientation(originalOrientation);
        } catch (Exception ignored) {}

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    private void maybeHideSplash() {
        if (webView.getProgress() >= 100) {
            firstPageLoaded = true;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                splashOverlay.setVisibility(View.GONE);
                topProgressBar.setVisibility(View.GONE);
            }, 180);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private void updateCacheMode() {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        if (isNetworkAvailable()) {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            offlineToastShown = false;
        } else {
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            if (!offlineToastShown) {
                offlineToastShown = true;
                Toast.makeText(this, "Offline mode: loading saved pages", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }


    private class MovieDownloadBridge {
        @JavascriptInterface
        public void startMovieDownload(final String url, final String movieTitle) {
            if (url == null || url.trim().isEmpty()) return;
            runOnUiThread(() -> {
                rememberMovieTitle(movieTitle);
                startDownload(
                        url,
                        webView != null ? webView.getSettings().getUserAgentString() : "MKMoviesAndroid/" + BuildConfig.VERSION_NAME,
                        "attachment",
                        guessMimeTypeFromUrl(url),
                        bestAvailableMovieTitle(movieTitle)
                );
            });
        }
    }

    private void injectDownloadClickInterceptor(WebView view) {
        if (view == null) return;
        String js = "(function(){"
                + "if(window.__mk_movies_download_interceptor_v2101)return;"
                + "window.__mk_movies_download_interceptor_v2101=true;"
                + "function isDownloadLink(a){"
                + "if(!a||!a.href)return false;"
                + "var h=String(a.href);"
                + "var c=(a.className||'').toString();"
                + "return a.hasAttribute('download')"
                + "|| c.indexOf('mkmc-theme-download-button')!==-1"
                + "|| c.indexOf('mk-watch-white__download')!==-1"
                + "|| c.indexOf('mkmc-watch-download')!==-1"
                + "|| c.indexOf('mkmc-download-button')!==-1"
                + "|| h.indexOf('mkmc_download_movie')!==-1"
                + "|| h.indexOf('action=mkmc_download_movie')!==-1"
                + "|| h.indexOf('response-content-disposition=')!==-1;"
                + "}"
                + "function text(el){return el&&el.textContent?el.textContent.trim():'';}"
                + "function movieTitle(a){"
                + "var v=a.getAttribute('data-movie-title')||a.getAttribute('data-title')||a.getAttribute('data-filename')||a.getAttribute('download')||a.getAttribute('aria-label')||'';"
                + "if(v&&!/^(download|download movie|watch now)$/i.test(v.trim()))return v;"
                + "var card=a.closest&&a.closest('article,.movie,.movie-card,.mkmc-movie-card,.post,.entry,.mk-watch-white');"
                + "var titleNode=card&&card.querySelector('.movie-title,.mk-movie-title,.entry-title,.post-title,.mkmc-title,.mk-watch-white__title,[itemprop=\"name\"],h1,h2,h3');"
                + "v=text(titleNode);if(v)return v;"
                + "v=text(document.querySelector('.mk-watch-white__title,.mkmc-watch-title,.movie-title,.mk-movie-title,.entry-title,.post-title,[itemprop=\"name\"],h1'));"
                + "if(v)return v;"
                + "var meta=document.querySelector('meta[property=\"og:title\"],meta[name=\"twitter:title\"]');"
                + "v=meta&&meta.content?meta.content.trim():'';if(v)return v;"
                + "return v||document.title||'MK Movies movie';"
                + "}"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target&&e.target.closest?e.target.closest('a'):null;"
                + "if(!isDownloadLink(a))return;"
                + "e.preventDefault();e.stopPropagation();"
                + "try{window.MKMoviesAndroidBridge.startMovieDownload(a.href,movieTitle(a));}catch(err){window.location.href=a.href;}"
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
        return url.contains("action=mkmc_download_movie")
                || url.contains("mkmc_download_movie")
                || url.contains("response-content-disposition=")
                || url.contains("download=");
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
        String host = uri.getHost();
        if (scheme == null) return false;

        if (("http".equals(scheme) || "https".equals(scheme))) {
            if (isMovieDownloadUrl(uri) || isLikelyMovieFileUrl(uri)) {
                startDownloadWithResolvedTitle(
                        uri.toString(),
                        webView != null ? webView.getSettings().getUserAgentString() : "MKMoviesAndroid/" + BuildConfig.VERSION_NAME,
                        "attachment",
                        guessMimeTypeFromUrl(uri.toString())
                );
                return true;
            }
            if (host != null && (host.equals("mkmovies.com.ng") || host.endsWith(".mkmovies.com.ng"))) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        } catch (Exception e) {
            return true;
        }
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

    private String movieTitleJavascript() {
        return "(function(){"
                + "function text(el){return el&&el.textContent?el.textContent.trim():'';}"
                + "var selectors=['.mk-watch-white__title','.mkmc-watch-title','.movie-title','.mk-movie-title','.entry-title','.post-title','[itemprop=\"name\"]','main h1','article h1','h1'];"
                + "for(var i=0;i<selectors.length;i++){var value=text(document.querySelector(selectors[i]));if(value)return value;}"
                + "var meta=document.querySelector('meta[property=\"og:title\"],meta[name=\"twitter:title\"]');"
                + "if(meta&&meta.content)return meta.content.trim();"
                + "return document.title||'';"
                + "})()";
    }

    private void captureCurrentMovieTitle(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(movieTitleJavascript(), value -> rememberMovieTitle(decodeJavascriptString(value)));
    }

    private void startDownloadWithResolvedTitle(String url, String userAgent, String contentDisposition, String mimeType) {
        if (webView == null) {
            startDownload(url, userAgent, contentDisposition, mimeType, lastKnownMovieTitle);
            return;
        }
        webView.evaluateJavascript(movieTitleJavascript(), value -> {
            String resolved = decodeJavascriptString(value);
            rememberMovieTitle(resolved);
            startDownload(url, userAgent, contentDisposition, mimeType, bestAvailableMovieTitle(resolved));
        });
    }

    private String decodeJavascriptString(String value) {
        if (value == null || value.trim().isEmpty() || "null".equals(value)) return "";
        try {
            return new JSONArray("[" + value + "]").getString(0);
        } catch (Exception ignored) {
            return value.replaceAll("^\"|\"$", "").replace("\\n", " ").replace("\\\"", "\"");
        }
    }

    private void rememberMovieTitle(String value) {
        String cleaned = cleanMovieTitle(value);
        if (isUsefulMovieTitle(cleaned)) lastKnownMovieTitle = cleaned;
    }

    private String bestAvailableMovieTitle(String preferred) {
        String cleaned = cleanMovieTitle(preferred);
        if (isUsefulMovieTitle(cleaned)) return cleaned;
        if (isUsefulMovieTitle(lastKnownMovieTitle)) return lastKnownMovieTitle;
        String pageTitle = webView != null ? cleanMovieTitle(webView.getTitle()) : "";
        return isUsefulMovieTitle(pageTitle) ? pageTitle : "";
    }

    private boolean isUsefulMovieTitle(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(Locale.US);
        String withoutExtension = lower.replaceFirst("\\.(mp4|mkv|webm|m4v|mov|avi|3gp)$", "");
        boolean generatedName = withoutExtension.matches("^mk[- _]?movies?[- _]?\\d{8,}.*")
                || withoutExtension.matches("^\\d{3,}[- _]\\d{6,}[- _].+")
                || withoutExtension.matches("^[a-f0-9]{18,}$");
        boolean hasLetters = withoutExtension.matches(".*[a-z].*");
        return lower.length() > 2
                && hasLetters
                && !generatedName
                && !lower.equals("download")
                && !lower.equals("download movie")
                && !lower.equals("watch")
                && !lower.equals("watch now")
                && !lower.equals("movie")
                && !lower.equals("mk movies")
                && !lower.equals("admin-post.php")
                && !lower.startsWith("attachment;")
                && !lower.endsWith(".bin");
    }

    private String cleanMovieTitle(String value) {
        if (value == null) return "";
        String cleaned;
        try {
            cleaned = Uri.decode(value);
        } catch (Exception ignored) {
            cleaned = value;
        }
        cleaned = cleaned.replaceAll("(?i)^(download|watch)\\s+", "");
        cleaned = cleaned.replaceAll("(?i)^(download|watch)[-_]+", "");
        cleaned = cleaned.replaceAll("(?i)\\s*[|–—]\\s*MK\\s*Movies.*$", "");
        cleaned = cleaned.replaceAll("(?i)\\s+-\\s+MK\\s*Movies.*$", "");
        cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|]", "-");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private String extensionFor(String url, String mimeType) {
        String lower = url == null ? "" : url.toLowerCase(Locale.US);
        String[] extensions = {".mp4", ".mkv", ".webm", ".m4v", ".mov", ".avi", ".3gp"};
        for (String extension : extensions) {
            if (lower.contains(extension)) return extension;
        }
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        if (mime.contains("matroska")) return ".mkv";
        if (mime.contains("webm")) return ".webm";
        if (mime.contains("m4v")) return ".m4v";
        if (mime.contains("quicktime")) return ".mov";
        if (mime.contains("msvideo") || mime.contains("avi")) return ".avi";
        if (mime.contains("3gpp")) return ".3gp";
        return ".mp4";
    }

    private boolean hasMovieExtension(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.US);
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                || lower.endsWith(".m4v") || lower.endsWith(".mov")
                || lower.endsWith(".avi") || lower.endsWith(".3gp");
    }

    private String cleanDownloadFilename(String url, String contentDisposition, String mimeType, String requestedTitle) {
        String filename = cleanMovieTitle(requestedTitle);

        if (!isUsefulMovieTitle(filename)) {
            try {
                Uri uri = Uri.parse(url);
                String[] keys = {"movie_title", "title", "filename", "file_name", "name", "download", "response-content-disposition"};
                for (String key : keys) {
                    String rawCandidate = uri.getQueryParameter(key);
                    String candidate;
                    if ("response-content-disposition".equals(key) && rawCandidate != null) {
                        candidate = cleanMovieTitle(URLUtil.guessFileName("https://mkmovies.com.ng/download", rawCandidate, mimeType));
                    } else {
                        candidate = cleanMovieTitle(rawCandidate);
                    }
                    if (isUsefulMovieTitle(candidate)) {
                        filename = candidate;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!isUsefulMovieTitle(filename)) {
            filename = cleanMovieTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
        }
        if (!isUsefulMovieTitle(filename)) {
            filename = "MK-Movies-" + System.currentTimeMillis();
        }
        if (!hasMovieExtension(filename)) filename += extensionFor(url, mimeType);

        if (filename.length() > 160) {
            String extension = extensionFor(filename, mimeType);
            filename = filename.substring(0, Math.max(1, 160 - extension.length())).trim() + extension;
        }
        return filename;
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType, String requestedTitle) {
        try {
            if (url == null || url.trim().isEmpty()) {
                Toast.makeText(this, "Download link is empty", Toast.LENGTH_SHORT).show();
                return;
            }

            String safeMimeType = (mimeType == null || mimeType.trim().isEmpty()) ? guessMimeTypeFromUrl(url) : mimeType;
            if (safeMimeType == null || safeMimeType.trim().isEmpty()) safeMimeType = "application/octet-stream";

            String filename = cleanDownloadFilename(url, contentDisposition, safeMimeType, requestedTitle);
            String safeUserAgent = userAgent != null ? userAgent : ("MKMoviesAndroid/" + BuildConfig.VERSION_NAME);

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies == null && url.contains("mkmovies.com.ng")) {
                cookies = CookieManager.getInstance().getCookie("https://mkmovies.com.ng/");
            }
            String referer = webView != null ? webView.getUrl() : "https://mkmovies.com.ng/";
            MovieDownloadService.start(
                    getApplicationContext(),
                    url.trim(),
                    filename,
                    safeMimeType,
                    safeUserAgent,
                    cookies,
                    referer
            );
            Toast.makeText(this, "Downloading " + filename, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w(TAG, "Movie download failed", e);
            Toast.makeText(this, "Download failed. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            if (currentNotificationPermission().equals("denied")) {
                Toast.makeText(this, "Notifications are off. Allow them in Android app settings to receive movie alerts.", Toast.LENGTH_LONG).show();
            }
            registerNativeToken();
        }
    }

    private String currentNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return "denied";
        }
        return NotificationManagerCompat.from(this).areNotificationsEnabled() ? "granted" : "denied";
    }

    private void registerNativeToken() {
        FirebaseMessaging.getInstance()
                .subscribeToTopic(MkFirebaseMessagingService.ALL_USERS_TOPIC)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to all-user FCM topic");
                    } else {
                        Log.w(TAG, "Could not subscribe to all-user FCM topic", task.getException());
                    }
                });
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                MkTokenRegistrar.registerTokenAsync(getApplicationContext(), task.getResult(), currentNotificationPermission());
                Log.d(TAG, "Native FCM token registered");
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
                    MkFirebaseMessagingService.CHANNEL_ID,
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

            // Older WordPress sender versions use this channel id in their FCM payload.
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView != null) webView.loadUrl(getLaunchUrl());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCacheMode();
        registerNativeToken();
    }

    @Override
    public void onBackPressed() {
        if (customFullscreenView != null) {
            hideFullscreenCustomView();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }
}
