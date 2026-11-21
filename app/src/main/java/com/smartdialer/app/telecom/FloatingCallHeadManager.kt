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
import com.smartdialer.app.R
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.ui.call.CallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages the compact floating call overlay / pill that stays on top of other apps
 * (like games) during an active voice call (both GSM and IP calls).
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
        val appContext = context.applicationContext
        if (floatingView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            return
        }

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
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
            x = dpToPx(appContext, 20)
            y = dpToPx(appContext, 120)
        }
        params = lp

        // Elegant floating pill container with deep translucent slate & neon border
        val rootView = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dpToPx(appContext, 14)
            val padV = dpToPx(appContext, 9)
            setPadding(padH, padV, padH, padV)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(appContext, 30).toFloat()
                setColor(Color.parseColor("#EB0F172A")) // Deep translucent navy/slate
                setStroke(dpToPx(appContext, 1.5f), Color.parseColor("#38BDF8")) // Neon Cyan-Blue border
            }
            background = bg
            elevation = dpToPx(appContext, 12).toFloat()
        }

        // Green Call Icon
        val iconView = ImageView(appContext).apply {
            setImageResource(R.drawable.ic_notification_incoming_call)
            setColorFilter(Color.parseColor("#10B981")) // Call green
            val iconSize = dpToPx(appContext, 20)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = dpToPx(appContext, 8)
            }
        }
        rootView.addView(iconView)

        // Texts: Caller Name + Live Duration Timer
        val textCol = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(appContext, 12)
            }
        }

        val nameView = TextView(appContext).apply {
            text = CallManager.currentCallState.value.displayTitle
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            paint.isFakeBoldText = true
        }
        textCol.addView(nameView)

        val timerView = TextView(appContext).apply {
            val d = CallManager.currentCallState.value.durationFormatted
            text = if (d.isNotBlank()) d else "In call"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        textCol.addView(timerView)

        rootView.addView(textCol)

        // 1. Mute / Unmute Button
        val muteBtn = ImageView(appContext).apply {
            val isMuted = CallManager.currentCallState.value.isMuted
            setImageResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic)
            setColorFilter(if (isMuted) Color.parseColor("#EF4444") else Color.WHITE)
            val size = dpToPx(appContext, 30)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(appContext, 8)
            }
            val bgCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isMuted) Color.parseColor("#44EF4444") else Color.parseColor("#26FFFFFF"))
            }
            background = bgCircle
            val p = dpToPx(appContext, 6)
            setPadding(p, p, p, p)
            setOnClickListener {
                CallManager.toggleMute()
            }
        }
        rootView.addView(muteBtn)

        // 2. Speaker / Loudspeaker Button
        val speakerBtn = ImageView(appContext).apply {
            val isSpeaker = CallManager.currentCallState.value.isSpeakerOn
            setImageResource(R.drawable.ic_speaker)
            setColorFilter(if (isSpeaker) Color.parseColor("#38BDF8") else Color.WHITE)
            val size = dpToPx(appContext, 30)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(appContext, 8)
            }
            val bgCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isSpeaker) Color.parseColor("#4438BDF8") else Color.parseColor("#26FFFFFF"))
            }
            background = bgCircle
            val p = dpToPx(appContext, 6)
            setPadding(p, p, p, p)
            setOnClickListener {
                CallManager.toggleSpeaker(appContext)
            }
        }
        rootView.addView(speakerBtn)

        // 3. End Call Button (Red Circle)
        val hangupBtn = ImageView(appContext).apply {
            setImageResource(R.drawable.ic_call_hangup)
            setColorFilter(Color.WHITE)
            val size = dpToPx(appContext, 30)
            layoutParams = LinearLayout.LayoutParams(size, size)
            val bgRed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444")) // Crimson red
            }
            background = bgRed
            val p = dpToPx(appContext, 6)
            setPadding(p, p, p, p)
            setOnClickListener {
                if (CallManager.currentCallState.value.status == CallStatus.RINGING) {
                    CallManager.reject()
                } else {
                    CallManager.disconnect()
                }
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
                        // Return to full-screen CallActivity on single tap
                        val intent = Intent(appContext, CallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        appContext.startActivity(intent)
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
                    timerView.text = if (state.durationFormatted.isNotBlank()) state.durationFormatted else "Connected"

                    // Update Mute UI
                    muteBtn.setImageResource(if (state.isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic)
                    muteBtn.setColorFilter(if (state.isMuted) Color.parseColor("#EF4444") else Color.WHITE)
                    (muteBtn.background as? GradientDrawable)?.setColor(
                        if (state.isMuted) Color.parseColor("#44EF4444") else Color.parseColor("#26FFFFFF")
                    )

                    // Update Speaker UI
                    speakerBtn.setColorFilter(if (state.isSpeakerOn) Color.parseColor("#38BDF8") else Color.WHITE)
                    (speakerBtn.background as? GradientDrawable)?.setColor(
                        if (state.isSpeakerOn) Color.parseColor("#4438BDF8") else Color.parseColor("#26FFFFFF")
                    )
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
