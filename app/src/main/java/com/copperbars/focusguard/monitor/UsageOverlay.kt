package com.copperbars.focusguard.monitor

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.os.Build

class UsageOverlay(
    private val context: Context,
    private val onPinSubmitted: (String, Int) -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var visiblePackage: String? = null

    fun show(packageName: String, appLabel: String, message: String) {
        if (root != null && visiblePackage == packageName) return
        hide()

        val background = FrameLayout(context).apply {
            setBackgroundColor(0xEE0D1220.toInt())
            isClickable = true
            isFocusableInTouchMode = true
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(30), dp(26), dp(28))
            background = rounded(0xFF171F32.toInt(), dp(28))
            elevation = dp(18).toFloat()
        }

        val title = TextView(context).apply {
            text = "FOCUS TIME"
            textSize = 14f
            letterSpacing = 0.16f
            setTextColor(0xFF9EA8FF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val headline = TextView(context).apply {
            text = "Süren doldu"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        val app = TextView(context).apply {
            text = appLabel
            textSize = 15f
            setTextColor(0xFFB8C2D9.toInt())
            gravity = Gravity.CENTER
        }
        val body = TextView(context).apply {
            text = message.ifBlank { "Şimdi ders, ödev veya görev zamanı." }
            textSize = 17f
            setTextColor(0xFFE3E8F4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(24))
        }
        val pin = EditText(context).apply {
            hint = "PIN kodun"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 18f
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8994AD.toInt())
            background = rounded(0xFF27324B.toInt(), dp(14))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val error = TextView(context).apply {
            text = "Kilidi açmak için PIN kodunu gir."
            textSize = 12f
            setTextColor(0xFFABB6CB.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, dp(9))
        }
        val choices = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(5, 10, 15, 0).forEach { minutes ->
            val choice = Button(context).apply {
                text = if (minutes == 0) "Oturum" else "$minutes dk"
                isAllCaps = false
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                background = rounded(0xFF666FEA.toInt(), dp(12))
                setPadding(dp(5), dp(7), dp(5), dp(7))
                setOnClickListener {
                    val value = pin.text?.toString().orEmpty()
                    if (value.isBlank()) {
                        error.text = "Önce PIN kodunu gir."
                        return@setOnClickListener
                    }
                    error.text = "Kontrol ediliyor…"
                    onPinSubmitted(value, minutes)
                }
            }
            val buttonParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            choices.addView(choice, buttonParams)
        }

        card.addView(title, matchWrap())
        card.addView(headline, matchWrap())
        card.addView(app, matchWrap())
        card.addView(body, matchWrap())
        card.addView(pin, matchWrap())
        card.addView(error, matchWrap())
        card.addView(choices, matchWrap())

        val params = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
        }
        background.addView(card, params)

        val windowType = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            dimAmount = 0.32f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            windowManager.addView(background, windowParams)
            root = background
            visiblePackage = packageName
            background.requestFocus()
        } catch (errorAdding: Exception) {
            Toast.makeText(context, "Odak ekranı gösterilemedi. Üstte gösterme iznini kontrol et.", Toast.LENGTH_LONG).show()
            root = null
            visiblePackage = null
        }
    }

    fun showPinError(message: String) {
        val view = root as? FrameLayout ?: return
        val card = view.getChildAt(0) as? LinearLayout ?: return
        val error = card.getChildAt(5) as? TextView ?: return
        error.text = message
    }

    fun hide() {
        val old = root ?: return
        try {
            windowManager.removeView(old)
        } catch (_: Exception) {
            // The window may already have been removed by Android.
        }
        root = null
        visiblePackage = null
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(old.windowToken, 0)
    }

    fun isVisibleFor(packageName: String): Boolean = root != null && visiblePackage == packageName

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
}
