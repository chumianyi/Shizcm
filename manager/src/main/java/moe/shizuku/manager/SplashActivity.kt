package moe.shizuku.manager

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.Logger.LOGGER

/**
 * Shizcm 开屏动画：
 *  - 全屏显示用户壁纸（淡入 + 轻微缩放）
 *  - 左侧竖排绿色文字「科技是为了服务人类」（霞鹜文楷开源字体，逐字弹性入场）
 *  - 底部「正在加载中」打字机动画 + 绿色加载指示器
 *  - 加载期间预热字体、配置、Root/无线调试/原版 Shizuku 环境检测，加载完成进入主界面
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val MIN_SPLASH_MS = 2600L
        private const val SLOGAN = "科技是为了服务人类"
        private const val LOADING_BASE = "正在加载中"
        private const val FONT_PATH = "fonts/LXGWWenKai-Regular.ttf"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var splashTypeface: Typeface = Typeface.DEFAULT
    private var resourcesReady = false
    private var minTimePassed = false
    private var finished = false

    private var loadingText: TextView? = null
    private var loadingDots = 0

    private val typingRunnable = object : Runnable {
        override fun run() {
            loadingDots = (loadingDots + 1) % 4
            val dots = ".".repeat(loadingDots)
            loadingText?.text = LOADING_BASE + dots
            mainHandler.postDelayed(this, 380)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        loadingText = findViewById(R.id.splash_loading_text)

        // 后台加载字体与资源/环境
        Thread {
            splashTypeface = try {
                Typeface.createFromAsset(assets, FONT_PATH)
            } catch (tr: Throwable) {
                LOGGER.w(tr, "createFromAsset $FONT_PATH")
                Typeface.DEFAULT
            }

            runOnUiThread { buildVerticalSlogan() }

            preloadEnvironment()

            resourcesReady = true
            mainHandler.post { maybeEnterHome() }
        }.start()

        startAnimations()

        // 最短展示时间
        mainHandler.postDelayed({
            minTimePassed = true
            maybeEnterHome()
        }, MIN_SPLASH_MS)
    }

    /** 左侧竖排文字：每个字一行，绿色 + 霞鹜文楷 + 弹性入场 */
    private fun buildVerticalSlogan() {
        val container = findViewById<View>(R.id.splash_chars)
        val density = resources.displayMetrics.density

        SLOGAN.forEachIndexed { index, ch ->
            val tv = TextView(this).apply {
                text = ch.toString()
                typeface = splashTypeface
                setTextColor(resources.getColor(R.color.shizcm_green_accent, theme))
                textSize = 28f
                includeFontPadding = false
                setShadowLayer(18f * density, 0f, 0f, resources.getColor(R.color.shizcm_green_dark, theme))
                alpha = 0f
                translationY = 26f * density
            }
            container.addView(tv)

            // 逐字弹性入场
            val fade = ObjectAnimator.ofFloat(tv, View.ALPHA, 0f, 1f)
            val slide = ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, 26f * density, 0f)
            fade.duration = 420
            slide.duration = 560
            slide.interpolator = OvershootInterpolator(1.7f)
            val set = AnimatorSet()
            set.playTogether(fade, slide)
            set.startDelay = 380 + index * 110L
            set.start()
        }
    }

    private fun startAnimations() {
        // 壁纸淡入 + 轻微放大
        val wallpaper = findViewById<View>(R.id.splash_wallpaper)
        val fade = ObjectAnimator.ofFloat(wallpaper, View.ALPHA, 0f, 1f)
        fade.duration = 1100
        fade.interpolator = AccelerateDecelerateInterpolator()

        val scale = ObjectAnimator.ofFloat(wallpaper, View.SCALE_X, 1.06f, 1f)
        val scaleY = ObjectAnimator.ofFloat(wallpaper, View.SCALE_Y, 1.06f, 1f)
        scale.duration = 1400
        scaleY.duration = 1400
        scale.interpolator = AccelerateDecelerateInterpolator()
        scaleY.interpolator = AccelerateDecelerateInterpolator()
        AnimatorSet().apply {
            playTogether(fade, scale, scaleY)
            start()
        }

        // 底部加载行淡入
        val loading = findViewById<View>(R.id.splash_loading)
        val loadFade = ObjectAnimator.ofFloat(loading, View.ALPHA, 0f, 1f)
        loadFade.duration = 600
        loadFade.startDelay = 700
        loadFade.start()

        // 打字机动画
        mainHandler.postDelayed(typingRunnable, 600)
    }

    /** 预热所有资源与环境检测（Root、无线调试、原版 Shizuku 共存检测） */
    private fun preloadEnvironment() {
        try {
            EnvironmentUtils.isRooted()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.getAdbTcpPort() > 0
        } catch (tr: Throwable) {
            LOGGER.w(tr, "preloadEnvironment")
        }

        try {
            // 检测原版 Shizuku 是否安装（用于激活状态互通显示）
            packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        } catch (tr: Throwable) {
            // 未安装，忽略
        }
    }

    private fun maybeEnterHome() {
        if (finished || !resourcesReady || !minTimePassed) {
            return
        }
        finished = true
        mainHandler.removeCallbacks(typingRunnable)

        val root = findViewById<View>(R.id.splash_chars)
        val fadeOut = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f)
        fadeOut.duration = 350
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        })
        fadeOut.start()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(typingRunnable)
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
