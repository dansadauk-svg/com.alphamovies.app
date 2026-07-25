package com.alphamovies.app

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

private lateinit var webView: WebView

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)

window.setStatusBarColor(android.graphics.Color.BLACK)

webView = WebView(this)

webView.settings.apply {
javaScriptEnabled = true
domStorageEnabled = true
mediaPlaybackRequiresUserGesture = false
loadWithOverviewMode = true
useWideViewPort = true
}

webView.loadUrl("https://alphamovies.com.ng")

setContentView(webView)
}

override fun onBackPressed() {
if(webView.canGoBack()) webView.goBack()
else super.onBackPressed()
}
}
