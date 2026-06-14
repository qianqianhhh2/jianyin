package com.qian.jianyin.playback

import android.annotation.SuppressLint
import com.qian.jianyin.LyricEntry
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.qian.jianyin.ui.JianYinTheme

class DesktopLyricService : Service() {

    private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        fun dispatchEvent(event: Lifecycle.Event) { lifecycleRegistry.handleLifecycleEvent(event) }
        fun performRestore(savedState: Bundle?) { savedStateRegistryController.performRestore(savedState) }
    }

    companion object {
        private const val TAG = "DesktopLyricService"
        
        private var currentLyrics = mutableListOf<LyricEntry>()
        private var currentLineIndex = 0
        private var isPlaying = false
        
        private var instance: DesktopLyricService? = null
        
        private var pendingUpdate = false

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

        fun updatePlayingState(playing: Boolean) {
            Log.d(TAG, "updatePlayingState: playing=$playing")
            isPlaying = playing
        }

        fun isRunning(): Boolean {
            val running = instance != null
            Log.d(TAG, "isRunning: $running")
            return running
        }

        fun updateFontSize(size: Float) {
            instance?.let {
                it.currentFontSize = size
                it.lyricTextView.textSize = size
            }
        }

        fun updateTextColor(@ColorInt color: Int) {
            instance?.let {
                it.currentTextColor = color
                it.lyricTextView.setTextColor(color)
            }
        }

        fun updateLockState(locked: Boolean) {
            instance?.let {
                it.isLocked = locked
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var rootLayout: FrameLayout
    private lateinit var lyricLayout: LinearLayout
    private lateinit var lyricTextView: TextView
    
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var screenWidth = 0
    private var screenHeight = 0
    
    private var displayedLyric = ""
    
    private var isLocked = false
    private var currentFontSize = 20f
    @ColorInt private var currentTextColor = Color.WHITE
    
    private var lastTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 300

    private var settingsComposeView: ComposeView? = null
    private var settingsLifecycleOwner: ServiceLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: DesktopLyricService created")
        instance = this
        
        loadSettings()
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            Log.d(TAG, "onCreate: WindowManager obtained successfully")
            
            createLyricWindow()
            Log.d(TAG, "onCreate: Lyric window created")
            
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
        val settings = DesktopLyricSettings.getSettings(this)
        currentFontSize = settings.fontSize
        currentTextColor = settings.textColor
        isLocked = settings.enabled
        Log.d(TAG, "loadSettings: fontSize=$currentFontSize, textColor=${Integer.toHexString(currentTextColor)}, isLocked=$isLocked, positionX=${settings.positionX}, positionY=${settings.positionY}")
    }

    @SuppressLint("InflateParams")
    private fun createLyricWindow() {
        Log.d(TAG, "createLyricWindow: Starting to create lyric window")
        
        try {
            val windowSize = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(windowSize)
            screenWidth = windowSize.x
            screenHeight = windowSize.y
            Log.d(TAG, "createLyricWindow: Screen size - width=$screenWidth, height=$screenHeight")
            
            val savedSettings = DesktopLyricSettings.getSettings(this)
            
            layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "createLyricWindow: Using TYPE_APPLICATION_OVERLAY (API 26+)")
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    Log.d(TAG, "createLyricWindow: Using TYPE_PHONE (API < 26)")
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                Log.d(TAG, "createLyricWindow: Flags set - NOT_FOCUSABLE, LAYOUT_IN_SCREEN, LAYOUT_NO_LIMITS")
                
                format = PixelFormat.TRANSLUCENT
                Log.d(TAG, "createLyricWindow: Format set to TRANSLUCENT")
                
                width = (resources.displayMetrics.widthPixels * 0.96).toInt()
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                val halfW = width / 2
                x = savedSettings.positionX.coerceIn(-halfW, halfW)
                y = savedSettings.positionY.coerceAtLeast(0)
                Log.d(TAG, "createLyricWindow: Position - x=$x, y=$y (loaded from settings)")
                
                alpha = 1f
            }
            
            val lifecycleOwner = ServiceLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_CREATE)
            settingsLifecycleOwner = lifecycleOwner
            
            rootLayout = FrameLayout(this).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }
            
            lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_RESUME)
            
            lyricLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16))

                lyricTextView = TextView(context).apply {
                    textSize = currentFontSize
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                lyricTextView.setTextColor(currentTextColor)
                addView(lyricTextView)
                Log.d(TAG, "createLyricWindow: lyricTextView created with color=${Integer.toHexString(currentTextColor)}")
            }
            rootLayout.addView(lyricLayout)
            Log.d(TAG, "createLyricWindow: All TextViews created with fontSize=$currentFontSize")
            Log.d(TAG, "createLyricWindow: Layout created")
            
            rootLayout.setOnTouchListener { _, event ->
                handleTouchEvent(event)
            }
            
            windowManager.addView(rootLayout, layoutParams)
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
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            if (settingsComposeView != null) {
                hideSettings()
                return true
            }
            return false
        }

        if (settingsComposeView != null) {
            val popupLocation = IntArray(2)
            settingsComposeView?.getLocationOnScreen(popupLocation)
            val popupW = settingsComposeView?.width ?: 0
            val popupH = settingsComposeView?.height ?: 0
            val inPopup = event.rawX >= popupLocation[0] && event.rawX <= popupLocation[0] + popupW &&
                    event.rawY >= popupLocation[1] && event.rawY <= popupLocation[1] + popupH
            if (inPopup) {
                return false
            }
        }

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
                        val newX = initialX + deltaX
                        val newY = initialY + deltaY
                        val halfW = layoutParams.width / 2
                        val viewH = rootLayout.height
                        layoutParams.x = newX.coerceIn(-halfW, halfW)
                        layoutParams.y = newY.coerceIn(0, (screenHeight - viewH).coerceAtLeast(0))
                        windowManager.updateViewLayout(rootLayout, layoutParams)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    DesktopLyricSettings.savePosition(this, layoutParams.x, layoutParams.y)
                    return true
                }
                
                if (settingsComposeView != null) {
                    hideSettings()
                    return true
                }
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                    Log.d(TAG, "Double tap detected, showing settings")
                    showSettings()
                    lastTapTime = 0
                    return true
                }
                lastTapTime = currentTime
                return false
            }
        }
        return false
    }

    private fun showSettings() {
        if (settingsComposeView != null) {
            hideSettings()
            return
        }
        
        try {
            layoutParams.flags = layoutParams.flags or
                    (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            windowManager.updateViewLayout(rootLayout, layoutParams)

            val composeView = ComposeView(this)
            
            composeView.setContent {
                JianYinTheme {
                    LyricSettingsPopupContent(
                        onDismiss = { hideSettings() }
                    )
                }
            }
            
            val composeParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
            composeView.layoutParams = composeParams
            
            lyricLayout.addView(composeView)
            settingsComposeView = composeView
            
            Log.d(TAG, "showSettings: Settings popup shown")
        } catch (e: Exception) {
            Log.e(TAG, "showSettings: Error showing settings popup", e)
        }
    }

    private fun hideSettings() {
        settingsComposeView?.let {
            try {
                lyricLayout.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "hideSettings: Error removing popup", e)
            }
        }
        settingsComposeView = null

        try {
            layoutParams.flags = layoutParams.flags and
                    (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH).inv()
            windowManager.updateViewLayout(rootLayout, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "hideSettings: Error updating flags", e)
        }
    }

    @Composable
    private fun LyricSettingsPopupContent(onDismiss: () -> Unit) {
        val context = LocalContext.current
        val settings = remember { DesktopLyricSettings.getSettings(context) }

        var fontSize by remember { mutableStateOf(settings.fontSize) }
        var textColor by remember { mutableStateOf(settings.textColor) }
        var locked by remember { mutableStateOf(settings.enabled) }

        val colorScheme = MaterialTheme.colorScheme

        val presetColors = remember {
            listOf(
                Color.WHITE,
                Color.parseColor("#E0E0E0"),
                Color.RED,
                Color.parseColor("#FF5722"),
                Color.parseColor("#FF9800"),
                Color.YELLOW,
                Color.GREEN,
                Color.parseColor("#4CAF50"),
                Color.BLUE,
                Color.parseColor("#2196F3"),
                Color.parseColor("#9C27B0"),
                Color.parseColor("#E91E63"),
                Color.parseColor("#FFD700"),
                Color.parseColor("#FF69B4"),
                Color.parseColor("#00BCD4")
            )
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "歌词设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.primary
                )

                Spacer(Modifier.height(16.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presetColors) { color ->
                        val selected = textColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color(color))
                                .then(
                                    if (selected) Modifier.border(
                                        1.dp,
                                        colorScheme.onSurface,
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable {
                                    textColor = color
                                    DesktopLyricService.updateTextColor(color)
                                    DesktopLyricSettings.saveSettings(context,
                                        DesktopLyricSettings(
                                            fontSize = fontSize,
                                            textColor = color,
                                            highlightColor = (0x80 shl 24) or ((color shr 16) and 0xFF shl 16) or ((color shr 8) and 0xFF shl 8) or (color and 0xFF),
                                            positionX = settings.positionX,
                                            positionY = settings.positionY,
                                            enabled = locked
                                        )
                                    )
                                }
                        )
                    }
                    item {
                        val accentColorInt = Color.argb(
                            (colorScheme.primary.alpha * 255).toInt(),
                            (colorScheme.primary.red * 255).toInt(),
                            (colorScheme.primary.green * 255).toInt(),
                            (colorScheme.primary.blue * 255).toInt()
                        )
                        val selected = textColor == accentColorInt
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary)
                                .then(
                                    if (selected) Modifier.border(
                                        1.dp,
                                        colorScheme.onSurface,
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable {
                                    textColor = accentColorInt
                                    DesktopLyricService.updateTextColor(accentColorInt)
                                    DesktopLyricSettings.saveSettings(context,
                                        DesktopLyricSettings(
                                            fontSize = fontSize,
                                            textColor = accentColorInt,
                                            highlightColor = (0x80 shl 24) or ((accentColorInt shr 16) and 0xFF shl 16) or ((accentColorInt shr 8) and 0xFF shl 8) or (accentColorInt and 0xFF),
                                            positionX = settings.positionX,
                                            positionY = settings.positionY,
                                            enabled = locked
                                        )
                                    )
                                }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "字号: ${fontSize.toInt()}sp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface
                )

                Spacer(Modifier.height(4.dp))

                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                        DesktopLyricService.updateFontSize(it)
                    },
                    onValueChangeFinished = {
                        DesktopLyricSettings.saveSettings(context,
                            DesktopLyricSettings(
                                fontSize = fontSize,
                                textColor = textColor,
                                highlightColor = (0x80 shl 24) or ((textColor shr 16) and 0xFF shl 16) or ((textColor shr 8) and 0xFF shl 8) or (textColor and 0xFF),
                                positionX = settings.positionX,
                                positionY = settings.positionY,
                                enabled = locked
                            )
                        )
                    },
                    valueRange = 12f..42f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "锁定位置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = locked,
                        onCheckedChange = {
                            locked = it
                            DesktopLyricService.updateLockState(it)
                            DesktopLyricSettings.saveSettings(context,
                                DesktopLyricSettings(
                                    fontSize = fontSize,
                                    textColor = textColor,
                                    highlightColor = (0x80 shl 24) or ((textColor shr 16) and 0xFF shl 16) or ((textColor shr 8) and 0xFF shl 8) or (textColor and 0xFF),
                                    positionX = settings.positionX,
                                    positionY = settings.positionY,
                                    enabled = locked
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    private fun updateLyricView() {
        Log.d(TAG, "updateLyricView: currentLineIndex=$currentLineIndex, lyrics.size=${currentLyrics.size}")

        lyricTextView.setTextColor(currentTextColor)
        lyricTextView.textSize = currentFontSize

        if (currentLyrics.isEmpty()) {
            Log.d(TAG, "updateLyricView: No lyrics to display")
            lyricTextView.text = ""
            return
        }

        val currentLine = if (currentLineIndex >= 0 && currentLineIndex < currentLyrics.size) {
            currentLyrics[currentLineIndex]
        } else {
            Log.w(TAG, "updateLyricView: Invalid line index $currentLineIndex")
            return
        }

        if (currentLine.text == displayedLyric) {
            Log.d(TAG, "updateLyricView: Same lyric, skipping update")
            return
        }

        Log.d(TAG, "updateLyricView: Displaying lyric: '${currentLine.text}'")

        displayedLyric = currentLine.text
        lyricTextView.text = displayedLyric
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: DesktopLyricService destroying")
        instance = null
        
        hideSettings()
        
        settingsLifecycleOwner?.dispatchEvent(Lifecycle.Event.ON_PAUSE)
        settingsLifecycleOwner?.dispatchEvent(Lifecycle.Event.ON_STOP)
        settingsLifecycleOwner?.dispatchEvent(Lifecycle.Event.ON_DESTROY)
        settingsLifecycleOwner = null
        
        try {
            windowManager.removeView(rootLayout)
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

data class DesktopLyricSettings(
    @Dimension(unit = Dimension.SP) val fontSize: Float = 20f,
    @ColorInt val textColor: Int = Color.WHITE,
    @ColorInt val highlightColor: Int = 0x80FFFFFF.toInt(),
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
        
        private fun safeGetInt(prefs: SharedPreferences, key: String, default: Int): Int {
            return try {
                prefs.getInt(key, default)
            } catch (e: ClassCastException) {
                try {
                    prefs.getString(key, null)?.toIntOrNull() ?: default
                } catch (e2: ClassCastException) {
                    default
                }
            }
        }

        fun getSettings(context: Context): DesktopLyricSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return DesktopLyricSettings(
                fontSize = prefs.getFloat(KEY_FONT_SIZE, 20f),
                textColor = safeGetInt(prefs, KEY_TEXT_COLOR, Color.WHITE),
                highlightColor = safeGetInt(prefs, KEY_HIGHLIGHT_COLOR, 0x80FFFFFF.toInt()),
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
