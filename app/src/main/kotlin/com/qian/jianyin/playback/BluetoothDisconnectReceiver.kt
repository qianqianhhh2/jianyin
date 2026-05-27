package com.qian.jianyin.playback

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

class BluetoothDisconnectReceiver(
    private val onDisconnect: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                Log.d("BTDisconnect", "音频输出设备断开（耳机/蓝牙设备断开）")
                onDisconnect()
            }
            
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                
                device?.let {
                    val deviceName = it.name ?: "未知设备"
                    val deviceAddress = it.address
                    Log.d("BTDisconnect", "蓝牙设备ACL断开: $deviceName ($deviceAddress)")
                    
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val isMusicActive = audioManager?.isMusicActive ?: false
                    
                    if (isMusicActive) {
                        Log.d("BTDisconnect", "音乐正在播放且蓝牙设备断开，暂停播放")
                        onDisconnect()
                    }
                }
            }
            
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d("BTDisconnect", "蓝牙已关闭")
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        val isMusicActive = audioManager?.isMusicActive ?: false
                        if (isMusicActive) {
                            onDisconnect()
                        }
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        Log.d("BTDisconnect", "蓝牙正在关闭")
                    }
                }
            }
        }
    }
}
