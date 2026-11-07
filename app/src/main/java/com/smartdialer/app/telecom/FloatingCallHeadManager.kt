package com.smartdialer.app.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.ui.call.CallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages the floating call bubble / chathead that stays on top of other apps
 * during an active voice call (both GSM and IP calls).
 */
object FloatingCallHeadManager {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var observerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun isShowing(): Boolean = floatingView != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context) {
        if (floatingView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 220
        }
        params = lp

        // Elegant floating pill container
        val rootView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dpToPx(context, 12)
            val padV = dpToPx(context, 8)
            setPadding(padH, padV, padH, padV)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 28).toFloat()
                setColor(Color.parseColor("#E61E1B4B")) // Deep translucent indigo
                setStroke(dpToPx(context, 1.5f), Color.parseColor("#8B5CF6")) // Neon Purple accent border
            }
            background = bg
            elevation = dpToPx(context, 8).toFloat()
        }

        // Green Call Icon
        val iconView = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_call)
            setColorFilter(Color.parseColor("#10B981")) // Call green
            val iconSize = dpToPx(context, 22)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = dpToPx(context, 8)
            }
        }
        rootView.addView(iconView)

        // Texts: Caller Name + Timer
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(context, 12)
            }
        }

        val nameView = TextView(context).apply {
            text = CallManager.currentCallState.value.displayTitle
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            paint.isFakeBoldText = true
        }
        textCol.addView(nameView)

        val timerView = TextView(context).apply {
            text = CallManager.currentCallState.value.durationFormatted
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        textCol.addView(timerView)

        rootView.addView(textCol)

        // Mute / Unmute Button
        val muteBtn = ImageView(context).apply {
            val isMuted = CallManager.currentCallState.value.isMuted
            setImageResource(if (isMuted) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_btn_speak_now)
            setColorFilter(if (isMuted) Color.parseColor("#EF4444") else Color.WHITE)
            val size = dpToPx(context, 28)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(context, 8)
            }
            val bgCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            background = bgCircle
            val p = dpToPx(context, 5)
            setPadding(p, p, p, p)
            setOnClickListener {
                CallManager.toggleMute()
            }
        }
        rootView.addView(muteBtn)

        // End Call Button
        val hangupBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            val size = dpToPx(context, 28)
            layoutParams = LinearLayout.LayoutParams(size, size)
            val bgRed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444"))
            }
            background = bgRed
            val p = dpToPx(context, 6)
            setPadding(p, p, p, p)
            setOnClickListener {
                CallManager.reject()
                dismiss()
            }
        }
        rootView.addView(hangupBtn)

        // Draggable Touch & Click Listener
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        rootView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }
                    lp.x = initialX + dx
                    lp.y = initialY + dy
                    try {
                        wm.updateViewLayout(rootView, lp)
                    } catch (e: Exception) {
                        // ignore if detached
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        // Return to full-screen CallActivity
                        val intent = Intent(context, CallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                        dismiss()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(rootView, lp)
            floatingView = rootView
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        // Keep floating view synchronized with ongoing call state and dismiss automatically on hang-up
        observerJob?.cancel()
        observerJob = scope.launch {
            CallManager.currentCallState.collectLatest { state ->
                if (state.status == CallStatus.DISCONNECTED || state.status == CallStatus.IDLE) {
                    dismiss()
                } else {
                    nameView.text = state.displayTitle
                    timerView.text = state.durationFormatted
                    muteBtn.setColorFilter(if (state.isMuted) Color.parseColor("#EF4444") else Color.WHITE)
                }
            }
        }
    }

    fun dismiss() {
        observerJob?.cancel()
        observerJob = null
        val view = floatingView
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // View already detached
            }
            floatingView = null
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(context: Context, dp: Int): Int = dpToPx(context, dp.toFloat())
}
