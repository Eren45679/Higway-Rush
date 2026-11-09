
package com.app.higwayrush

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var lastBack = 0L

    private val testRewardedAdUnitId =
        "ca-app-pub-3940256099942544/5224354917"

    private val host = "appassets.androidplatform.net"
    private val startUrl =
        "https://appassets.androidplatform.net/assets/www/index.html"
    private val keepInsideApp = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#090a0d"))
        setContentView(web)

        web.addJavascriptInterface(AdsBridge(), "AndroidAds")

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
        }

        web.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url

                if (uri.host == host) return false

                val scheme = uri.scheme
                if ((scheme == "http" || scheme == "https") && keepInsideApp) {
                    return false
                }

                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                }

                return true
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (web.canGoBack()) {
                        web.goBack()
                    } else {
                        val now = System.currentTimeMillis()

                        if (now - lastBack < 2000) {
                            finish()
                        } else {
                            lastBack = now
                            Toast.makeText(
                                this@MainActivity,
                                "Press back again to exit",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )

        if (savedInstanceState == null) {
            web.loadUrl(startUrl)
        } else {
            web.restoreState(savedInstanceState)
        }

        initializeAds()
    }

    private fun initializeAds() {
        Thread {
            MobileAds.initialize(
                this,
                InitializationConfig.Builder(
                    "ca-app-pub-1921898456062274~1373660877"
                ).build()
            ) {
                val request = AdRequest.Builder(testRewardedAdUnitId).build()
                val config = PreloadConfiguration(request)

                RewardedAdPreloader.start(
                    testRewardedAdUnitId,
                    config
                )
            }
        }.start()
    }

    private fun showRewardedAdNow() {
        val ad = RewardedAdPreloader.pollAd(testRewardedAdUnitId)

        if (ad == null) {
            web.evaluateJavascript(
                "window.onHighwayRewardedAdUnavailable && window.onHighwayRewardedAdUnavailable();",
                null
            )
            return
        }

        ad.show(this) { rewardItem ->
            runOnUiThread {
                web.evaluateJavascript(
                    "window.onHighwayRewardedAdCompleted && window.onHighwayRewardedAdCompleted(${rewardItem.amount});",
                    null
                )
            }
        }

        RewardedAdPreloader.start(
            testRewardedAdUnitId,
            PreloadConfiguration(
                AdRequest.Builder(testRewardedAdUnitId).build()
            )
        )
    }

    private inner class AdsBridge {

        @JavascriptInterface
        fun showRewardedAd() {
            runOnUiThread {
                showRewardedAdNow()
            }
        }
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
