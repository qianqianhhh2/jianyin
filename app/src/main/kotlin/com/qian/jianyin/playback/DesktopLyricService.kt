package com.qian.jianyin.playback

import android.annotation.SuppressLint
import com.qian.jianyin.LyricEntry
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ToggleButton
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 桌面歌词服务
 * 
 * 提供悬浮窗歌词显示功能，支持拖动、自定义样式等特性。
 */
class DesktopLyricService : Service() {

    companion object {
        private const val TAG = "DesktopLyricService"
        
        // 当前歌词数据
        private var currentLyrics = mutableListOf<LyricEntry>()
        private var currentLineIndex = 0
        private var isPlaying = false
        
        // 服务实例引用
        private var instance: DesktopLyricService? = null
        
        // 是否有待处理的歌词更新
        private var pendingUpdate = false
        
        /**
         * 更新歌词数据
         */
        fun updateLyric(lyrics: List<LyricEntry>, lineIndex: Int, playing: Boolean = true) {
            Log.d(TAG, "updateLyric: lyrics size=${lyrics.size}, lineIndex=$lineIndex, playing=$playing")
            currentLyrics.clear()
            currentLyrics.addAll(lyrics)
            currentLineIndex = lineIndex
            isPlaying = playing
            pendingUpdate = true
            
            if (instance != null) {
                Log.d(TAG, "updateLyric: instance exists, calling updateLyricView")
                instance?.updateLyricView()
                pendingUpdate = false
            } else {
                Log.w(TAG, "updateLyric: instance is null, lyrics saved for later update")
            }
        }
        
        /**
         * 更新播放状态
         */
        fun updatePlayingState(playing: Boolean) {
            Log.d(TAG, "updatePlayingState: playing=$playing")
            isPlaying = playing
        }
        
        /**
         * 检查服务是否正在运行
         */
        fun isRunning(): Boolean {
            val running = instance != null
            Log.d(TAG, "isRunning: $running")
            return running
        }
    }

    // 悬浮窗参数
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var lyricLayout: LinearLayout
    private lateinit var lyricTextView: TextView
    private lateinit var lyricTextView2: TextView
    
    // 拖动相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    
    // 歌词状态
    private var displayedLyric = ""
    private var displayedLyric2 = ""
    
    // 动画协程
    private var fadeJob: Job? = null
    
    // 设置相关
    private var isLocked = false
    private var currentFontSize = 20f
    @ColorInt private var currentTextColor = Color.WHITE
    @ColorInt private var currentSecondaryColor = Color.parseColor("#80FFFFFF")
    
    // 设置弹窗
    private var settingsPopup: PopupWindow? = null
    private var longPressStartTime = 0L
    private var isLongPress = false
    
    // 双击检测
    private var lastTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 300 // 双击间隔阈值（毫秒）

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: DesktopLyricService created")
        instance = this
        
        // 加载保存的设置
        loadSettings()
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            Log.d(TAG, "onCreate: WindowManager obtained successfully")
            
            createLyricWindow()
            Log.d(TAG, "onCreate: Lyric window created")
            
            // 检查是否有待处理的歌词更新
            if (pendingUpdate) {
                Log.d(TAG, "onCreate: Found pending lyric update, applying now")
                updateLyricView()
                pendingUpdate = false
            } else {
                updateLyricView()
            }
            Log.d(TAG, "onCreate: Lyric view updated")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error creating service", e)
        }
    }

    private fun loadSettings() {
        Log.d(TAG, "loadSettings: Checking if settings exist...")
        if (DesktopLyricSettings.hasSettings(this)) {
            val settings = DesktopLyricSettings.getSettings(this)
            Log.d(TAG, "loadSettings: Settings exist, loading values:")
            Log.d(TAG, "  - fontSize: ${settings.fontSize}")
            Log.d(TAG, "  - textColor: ${Integer.toHexString(settings.textColor)}")
            Log.d(TAG, "  - highlightColor: ${Integer.toHexString(settings.highlightColor)}")
            Log.d(TAG, "  - positionX: ${settings.positionX}")
            Log.d(TAG, "  - positionY: ${settings.positionY}")
            Log.d(TAG, "  - enabled (isLocked): ${settings.enabled}")
            
            currentFontSize = settings.fontSize
            currentTextColor = settings.textColor
            currentSecondaryColor = settings.highlightColor
            isLocked = settings.enabled
            
            Log.d(TAG, "loadSettings: Successfully loaded settings into variables")
        } else {
            Log.d(TAG, "loadSettings: No saved settings found, using default values:")
            Log.d(TAG, "  - fontSize: $currentFontSize (default)")
            Log.d(TAG, "  - textColor: ${Integer.toHexString(currentTextColor)} (default)")
            Log.d(TAG, "  - highlightColor: ${Integer.toHexString(currentSecondaryColor)} (default)")
            Log.d(TAG, "  - isLocked: $isLocked (default)")
        }
    }

    @SuppressLint("InflateParams")
    private fun createLyricWindow() {
        Log.d(TAG, "createLyricWindow: Starting to create lyric window")
        
        try {
            val windowSize = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(windowSize)
            Log.d(TAG, "createLyricWindow: Screen size - width=${windowSize.x}, height=${windowSize.y}")
            
            // 加载保存的位置
            val savedSettings = DesktopLyricSettings.getSettings(this)
            
            layoutParams = WindowManager.LayoutParams().apply {
                // 设置悬浮窗类型
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "createLyricWindow: Using TYPE_APPLICATION_OVERLAY (API 26+)")
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    Log.d(TAG, "createLyricWindow: Using TYPE_PHONE (API < 26)")
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                // 设置标志
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                Log.d(TAG, "createLyricWindow: Flags set - NOT_FOCUSABLE, LAYOUT_IN_SCREEN, LAYOUT_NO_LIMITS")
                
                // 设置格式
                format = PixelFormat.TRANSLUCENT
                Log.d(TAG, "createLyricWindow: Format set to TRANSLUCENT")
                
                // 设置位置和大小 - 应用保存的位置
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                x = savedSettings.positionX
                y = savedSettings.positionY
                Log.d(TAG, "createLyricWindow: Position - x=$x, y=$y (loaded from settings)")
                
                // 设置透明度
                alpha = 1f
            }
            
            // 创建布局
            lyricLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16))
                
                // 创建上一句歌词TextView
                lyricTextView2 = TextView(context).apply {
                    textSize = currentFontSize * 0.7f
                    setTextColor(currentSecondaryColor)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(8)
                    }
                }
                addView(lyricTextView2)
                Log.d(TAG, "createLyricWindow: lyricTextView2 created with color=${Integer.toHexString(currentSecondaryColor)}")
                
                // 创建当前歌词TextView
                lyricTextView = TextView(context).apply {
                    textSize = currentFontSize
                    setTextColor(currentTextColor)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                addView(lyricTextView)
                Log.d(TAG, "createLyricWindow: lyricTextView created with color=${Integer.toHexString(currentTextColor)}")
                
                setOnTouchListener { _, event ->
                    handleTouchEvent(event)
                }
            }
            Log.d(TAG, "createLyricWindow: All TextViews created with fontSize=$currentFontSize")
            Log.d(TAG, "createLyricWindow: Layout created")
            
            windowManager.addView(lyricLayout, layoutParams)
            Log.d(TAG, "createLyricWindow: Window added to WindowManager successfully")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "createLyricWindow: SecurityException - Missing SYSTEM_ALERT_WINDOW permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "createLyricWindow: Error creating lyric window", e)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isLocked) {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isLocked) {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        windowManager.updateViewLayout(lyricLayout, layoutParams)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    // 保存位置
                    isDragging = false
                    DesktopLyricSettings.savePosition(this, layoutParams.x, layoutParams.y)
                    return true
                }
                
                // 双击检测（无论是否锁定）
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                    // 双击打开设置菜单
                    Log.d(TAG, "Double tap detected, showing settings popup")
                    showSettingsPopup(initialTouchX.toInt(), initialTouchY.toInt())
                    lastTapTime = 0 // 重置，防止连续触发
                    return true
                }
                lastTapTime = currentTime
                return false
            }
        }
        return false
    }

    private fun showSettingsPopup(x: Int, y: Int) {
        Log.d(TAG, "showSettingsPopup: Showing settings popup at ($x, $y)")
        
        // 保存当前位置
        val currentX = layoutParams.x
        val currentY = layoutParams.y
        
        // 如果弹窗已显示，先关闭
        settingsPopup?.dismiss()
        
        // 创建设置布局
        val settingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setBackgroundColor(Color.parseColor("#CC000000"))
            
            // 标题
            addView(TextView(context).apply {
                text = "歌词设置"
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(12)
                }
            })
            
            // 颜色选择区域（可滑动）
            val colorScrollView = android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(48)
                ).apply {
                    bottomMargin = dpToPx(12)
                }
                
                // 颜色列表
                val colorLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dpToPx(4), 0, dpToPx(4), 0)
                }
                
                // 预设颜色按钮
                val colors = arrayOf(
                    Color.WHITE,
                    Color.BLACK,
                    Color.RED,
                    Color.GREEN,
                    Color.BLUE,
                    Color.YELLOW,
                    Color.parseColor("#FFD700"), // 金色
                    Color.parseColor("#FF69B4"), // 粉色
                    Color.parseColor("#9B59B6"), // 紫色
                    Color.parseColor("#795548")  // 棕色
                )
                
                colors.forEach { color ->
                    colorLayout.addView(View(context).apply {
                        setBackgroundColor(color)
                        layoutParams = LinearLayout.LayoutParams(
                            dpToPx(32),
                            dpToPx(32)
                        ).apply {
                            marginEnd = dpToPx(8)
                            topMargin = dpToPx(8)
                        }
                        // 添加边框
                        setBackgroundResource(android.R.drawable.btn_default)
                        // 设置实际颜色
                        setBackgroundColor(color)
                        setOnClickListener {
                            currentTextColor = color
                            lyricTextView.setTextColor(color)
                            // 更新次要颜色为主颜色的半透明版本（保留RGB，设置alpha为50%）
                            val alpha = 0x80 // 50% 透明度
                            val red = (color shr 16) and 0xFF
                            val green = (color shr 8) and 0xFF
                            val blue = color and 0xFF
                            currentSecondaryColor = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                            lyricTextView2.setTextColor(currentSecondaryColor)
                            // 保存颜色设置
                            DesktopLyricSettings.saveSettings(this@DesktopLyricService, 
                                DesktopLyricSettings(fontSize = currentFontSize, textColor = color, 
                                    highlightColor = currentSecondaryColor,
                                    positionX = currentX, positionY = currentY,
                                    enabled = isLocked))
                            Log.d(TAG, "Color changed to: ${Integer.toHexString(color)}")
                        }
                    })
                }
                
                addView(colorLayout)
            }
            addView(colorScrollView)
            
            // 字号调节
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                
                addView(TextView(context).apply {
                    text = "字号: ${currentFontSize.toInt()}sp"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(8)
                    }
                })
                
                addView(SeekBar(context).apply {
                    max = 30
                    progress = (currentFontSize - 12).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(12)
                    }
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            currentFontSize = (12 + progress).toFloat()
                            lyricTextView.textSize = currentFontSize
                            lyricTextView2.textSize = currentFontSize * 0.7f
                            ((parent as LinearLayout).getChildAt(0) as TextView).text = "字号: ${currentFontSize.toInt()}sp"
                        }
                        
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            DesktopLyricSettings.saveSettings(this@DesktopLyricService,
                                DesktopLyricSettings(fontSize = currentFontSize, textColor = currentTextColor,
                                    highlightColor = currentSecondaryColor,
                                    positionX = currentX, positionY = currentY,
                                    enabled = isLocked))
                        }
                    })
                })
            })
            
            // 锁定开关
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                
                addView(TextView(context).apply {
                    text = "锁定位置"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                })
                
                addView(ToggleButton(context).apply {
                    isChecked = isLocked
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnCheckedChangeListener { _, isChecked ->
                        isLocked = isChecked
                        // 保存锁定状态
                        DesktopLyricSettings.saveSettings(this@DesktopLyricService,
                            DesktopLyricSettings(fontSize = currentFontSize, textColor = currentTextColor,
                                highlightColor = currentSecondaryColor, positionX = this@DesktopLyricService.layoutParams.x,
                                positionY = this@DesktopLyricService.layoutParams.y, enabled = isLocked))
                        Log.d(TAG, "showSettingsPopup: Lock state changed to $isLocked, settings saved")
                    }
                })
            })
        }
        
        settingsPopup = PopupWindow(
            settingsLayout,
            dpToPx(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setOnDismissListener {
                settingsPopup = null
            }
        }
        
        // 显示弹窗
        try {
            settingsPopup?.showAtLocation(lyricLayout, Gravity.NO_GRAVITY, x - dpToPx(140), y - dpToPx(80))
            Log.d(TAG, "showSettingsPopup: Popup shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "showSettingsPopup: Error showing popup", e)
        }
    }

    private fun updateLyricView() {
        Log.d(TAG, "updateLyricView: currentLineIndex=$currentLineIndex, lyrics.size=${currentLyrics.size}")
        
        if (currentLyrics.isEmpty()) {
            Log.d(TAG, "updateLyricView: No lyrics to display")
            lyricTextView.text = ""
            lyricTextView2.text = ""
            return
        }
        
        // 获取当前歌词
        val currentLine = if (currentLineIndex >= 0 && currentLineIndex < currentLyrics.size) {
            currentLyrics[currentLineIndex]
        } else {
            Log.w(TAG, "updateLyricView: Invalid line index $currentLineIndex")
            return
        }
        
        // 如果歌词相同，不更新
        if (currentLine.text == displayedLyric) {
            Log.d(TAG, "updateLyricView: Same lyric, skipping update")
            return
        }
        
        Log.d(TAG, "updateLyricView: Displaying lyric: '${currentLine.text}'")
        
        // 保存上一句歌词用于淡出效果
        if (displayedLyric.isNotEmpty()) {
            displayedLyric2 = displayedLyric
            lyricTextView2.text = displayedLyric2
            lyricTextView2.alpha = 0.5f
            
            // 淡出动画
            fadeJob?.cancel()
            fadeJob = CoroutineScope(Dispatchers.Main).launch {
                for (i in 0..20) {
                    lyricTextView2.alpha = 0.5f - (i * 0.025f)
                    delay(20)
                }
                displayedLyric2 = ""
                lyricTextView2.text = ""
            }
        }
        
        // 更新当前歌词
        displayedLyric = currentLine.text
        lyricTextView.text = displayedLyric
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: DesktopLyricService destroying")
        instance = null
        fadeJob?.cancel()
        settingsPopup?.dismiss()
        
        try {
            windowManager.removeView(lyricLayout)
            Log.d(TAG, "onDestroy: Window removed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error removing window", e)
        }
        
        super.onDestroy()
        Log.d(TAG, "onDestroy: DesktopLyricService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

/**
 * 桌面歌词设置数据类
 */
data class DesktopLyricSettings(
    @Dimension(unit = Dimension.SP) val fontSize: Float = 20f,
    @ColorInt val textColor: Int = Color.WHITE,
    @ColorInt val highlightColor: Int = Color.parseColor("#80FFFFFF"),
    val positionX: Int = 0,
    val positionY: Int = 200,
    val enabled: Boolean = false
) {
    companion object {
        private const val PREFS_NAME = "desktop_lyric_settings"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_HIGHLIGHT_COLOR = "highlight_color"
        private const val KEY_POSITION_X = "position_x"
        private const val KEY_POSITION_Y = "position_y"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SETTINGS_EXIST = "settings_exist"
        
        fun hasSettings(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SETTINGS_EXIST, false)
        }
        
        fun getSettings(context: Context): DesktopLyricSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return DesktopLyricSettings(
                fontSize = prefs.getFloat(KEY_FONT_SIZE, 20f),
                textColor = prefs.getInt(KEY_TEXT_COLOR, Color.WHITE),
                highlightColor = prefs.getInt(KEY_HIGHLIGHT_COLOR, Color.parseColor("#80FFFFFF")),
                positionX = prefs.getInt(KEY_POSITION_X, 0),
                positionY = prefs.getInt(KEY_POSITION_Y, 200),
                enabled = prefs.getBoolean(KEY_ENABLED, false)
            )
        }
        
        fun saveSettings(context: Context, settings: DesktopLyricSettings) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val success = prefs.edit()
                .putFloat(KEY_FONT_SIZE, settings.fontSize)
                .putInt(KEY_TEXT_COLOR, settings.textColor)
                .putInt(KEY_HIGHLIGHT_COLOR, settings.highlightColor)
                .putInt(KEY_POSITION_X, settings.positionX)
                .putInt(KEY_POSITION_Y, settings.positionY)
                .putBoolean(KEY_ENABLED, settings.enabled)
                .putBoolean(KEY_SETTINGS_EXIST, true)
                .commit()
            Log.d("DesktopLyricSettings", "saveSettings: success=$success, fontSize=${settings.fontSize}, textColor=${Integer.toHexString(settings.textColor)}, enabled=${settings.enabled}")
        }
        
        fun savePosition(context: Context, x: Int, y: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_POSITION_X, x)
                .putInt(KEY_POSITION_Y, y)
                .putBoolean(KEY_SETTINGS_EXIST, true)
                .apply()
        }
        
        fun setEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_SETTINGS_EXIST, true)
                .apply()
        }
    }
}