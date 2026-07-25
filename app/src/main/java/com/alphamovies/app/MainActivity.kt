package com.alphamovies.app

import android.os.Bundle
import android.graphics.Color
import android.webkit.*
import android.view.Window
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

private lateinit var webView: WebView

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)

WindowInsetsControllerCompat(window, window.decorView)

window.statusBarColor = Color.BLACK

webView = WebView(this)

webView.settings.apply {
javaScriptEnabled = true
domStorageEnabled = true
mediaPlaybackRequiresUserGesture = false
loadWithOverviewMode = true
useWideViewPort = true
}

webView.webViewClient = WebViewClient()

webView.webChromeClient = WebChromeClient()

webView.loadUrl("https://alphamovies.com.ng")

setContentView(webView)
}

override fun onBackPressed() {
if(webView.canGoBack()) {
webView.goBack()
} else {
super.onBackPressed()
}
}
}
