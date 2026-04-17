package com.qian.jianyin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * 权限管理类
 * 用于检查和申请应用所需的权限
 */
object PermissionManager {
    /**
     * 获取应用需要的危险权限列表
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        // 存储权限
        if (Build.VERSION.SDK_INT <= 32) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= 28) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        
        // 通知权限
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        return permissions
    }
    
    /**
     * 检查是否需要申请MANAGE_EXTERNAL_STORAGE权限
     */
    fun needsManageExternalStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT >= 30
    }
    
    /**
     * 检查是否已授予MANAGE_EXTERNAL_STORAGE权限
     */
    fun hasManageExternalStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
    
    /**
     * 跳转到MANAGE_EXTERNAL_STORAGE权限设置页面
     */
    fun openManageExternalStorageSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        context.startActivity(intent)
    }
}

/**
 * 权限检查和申请组件
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionCheck() {
    val context = LocalContext.current
    val permissions = PermissionManager.getRequiredPermissions()
    val needsManageStorage = PermissionManager.needsManageExternalStoragePermission()
    val hasManageStoragePermission = remember {
        mutableStateOf(PermissionManager.hasManageExternalStoragePermission(context))
    }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showManageStorageDialog by remember { mutableStateOf(false) }
    var missingPermissions by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val permissionState = rememberMultiplePermissionsState(
        permissions = permissions
    ) {
        val deniedPermissions = it.filter { !it.value }.keys.toList()
        if (deniedPermissions.isNotEmpty()) {
            missingPermissions = deniedPermissions
            showPermissionDialog = true
        }
    }
    
    // 启动时检查权限
    LaunchedEffect(Unit) {
        // 直接请求权限，由回调处理权限结果
        permissionState.launchMultiplePermissionRequest()
        
        // 检查MANAGE_EXTERNAL_STORAGE权限
        if (needsManageStorage && !hasManageStoragePermission.value) {
            showManageStorageDialog = true
        }
    }
    
    // 权限缺失弹窗
    if (showPermissionDialog) {
        val permissionNames = missingPermissions.map { getPermissionName(it) }
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("权限申请", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "应用需要以下权限才能正常运行：\n\n" +
                    permissionNames.joinToString("\n") + "\n\n" +
                    "缺少这些权限可能会影响应用体验。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    permissionState.launchMultiplePermissionRequest()
                    showPermissionDialog = false
                }) {
                    Text("申请权限")
                }
            },
            dismissButton = {
                Button(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    
    // MANAGE_EXTERNAL_STORAGE权限申请弹窗
    if (showManageStorageDialog) {
        AlertDialog(
            onDismissRequest = { showManageStorageDialog = false },
            title = { Text("存储权限申请", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "应用需要管理所有文件的权限才能正常扫描和播放本地音乐。\n\n" +
                    "请在设置中允许应用访问所有文件。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    PermissionManager.openManageExternalStorageSettings(context)
                    showManageStorageDialog = false
                }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                Button(onClick = { showManageStorageDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * 获取权限的中文名称
 */
private fun getPermissionName(permission: String): String {
    return when (permission) {
        Manifest.permission.READ_EXTERNAL_STORAGE -> "读取外部存储"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "写入外部存储"
        Manifest.permission.READ_MEDIA_AUDIO -> "读取音频文件"
        Manifest.permission.READ_MEDIA_IMAGES -> "读取图片文件"
        Manifest.permission.POST_NOTIFICATIONS -> "发送通知"
        else -> permission
    }
}