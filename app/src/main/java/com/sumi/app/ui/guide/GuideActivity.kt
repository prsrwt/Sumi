package com.sumi.app.ui.guide

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Inside Sumi, the study guide, shown inside the app.
 *
 * The guide is the same web page as docs/ in the repository, packed into the
 * app's assets by the build, so it works offline. A WebView shows it; with no
 * connection, the page falls back from its web fonts to the phone's own.
 */
class GuideActivity : ComponentActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val paper = if (dark) PAPER_DARK else PAPER_LIGHT

        web = WebView(this).apply {
            setBackgroundColor(paper)
            settings.javaScriptEnabled = true
            // Only the bundled page is ever loaded here, so the bridge below is
            // never offered to anything from the web.
            addJavascriptInterface(AppBridge(dark), "SumiApp")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.scheme == "file") return false
                    // Links out of the guide, such as the repository, open in the browser.
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (e: ActivityNotFoundException) {
                        // Nothing on the phone can open it; stay on the guide.
                    }
                    return true
                }
            }
        }

        val frame = FrameLayout(this).apply {
            setBackgroundColor(paper)
            addView(web)
        }
        // Edge to edge: keep the page clear of the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(frame)

        // Back closes an open slideshow first, then the guide.
        onBackPressedDispatcher.addCallback(this) {
            web.evaluateJavascript("window.sumiBack ? window.sumiBack() : false") { consumed ->
                if (consumed != "true") finish()
            }
        }

        if (savedInstanceState == null) {
            web.loadUrl(GUIDE_URL)
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** What the page may ask the app. Kept to reading the theme. */
    private class AppBridge(private val dark: Boolean) {
        @JavascriptInterface
        fun theme(): String = if (dark) "dark" else "light"
    }

    private companion object {
        const val GUIDE_URL = "file:///android_asset/guide/index.html"
        const val PAPER_LIGHT = 0xFFF7F5F1.toInt()
        const val PAPER_DARK = 0xFF0B0D0C.toInt()
    }
}
