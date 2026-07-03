package com.qian.jianyin.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 国产系统厂商兼容工具
 *
 * 国产 Android 系统（华为鸿蒙、小米 HyperOS/MIUI、OPPO ColorOS、
 * VIVO OriginOS、荣耀 MagicOS、魅族 Flyme 等）普遍对后台进程有
 * 额外限制。本工具提供：
 * 1. 检测当前厂商
 * 2. 引导用户跳转至对应厂商的自启动/后台运行设置页面
 * 3. 电池优化白名单引导
 */
object ManufacturerCompat {

    private const val TAG = "ManufacturerCompat"

    enum class Manufacturer {
        HUAWEI,     // 华为 / 鸿蒙
        XIAOMI,     // 小米 / HyperOS / MIUI
        OPPO,       // OPPO / ColorOS
        VIVO,       // VIVO / OriginOS
        MEIZU,      // 魅族 / Flyme
        SAMSUNG,    // 三星 / One UI
        HONOR,      // 荣耀 / MagicOS
        LENOVO,     // 联想 / ZUI
        GENERIC     // 通用
    }

    /**
     * 检测当前设备厂商
     */
    fun detectManufacturer(): Manufacturer {
        val brand = Build.BRAND.uppercase()
        val manufacturer = Build.MANUFACTURER.uppercase()

        return when {
            brand.contains("HUAWEI") || brand.contains("HONOR") &&
                manufacturer.contains("HUAWEI") -> Manufacturer.HUAWEI
            manufacturer.contains("HONOR") -> Manufacturer.HONOR
            brand.contains("XIAOMI") || brand.contains("REDMI") ||
                manufacturer.contains("XIAOMI") -> Manufacturer.XIAOMI
            brand.contains("OPPO") || brand.contains("REALME") ||
                brand.contains("ONEPLUS") || manufacturer.contains("OPPO") -> Manufacturer.OPPO
            brand.contains("VIVO") || brand.contains("IQOO") ||
                manufacturer.contains("VIVO") -> Manufacturer.VIVO
            brand.contains("MEIZU") || manufacturer.contains("MEIZU") -> Manufacturer.MEIZU
            brand.contains("SAMSUNG") || manufacturer.contains("SAMSUNG") -> Manufacturer.SAMSUNG
            brand.contains("LENOVO") || manufacturer.contains("LENOVO") -> Manufacturer.LENOVO
            else -> Manufacturer.GENERIC
        }
    }

    /**
     * 获取厂商名称（中文）
     */
    fun getManufacturerName(): String = when (detectManufacturer()) {
        Manufacturer.HUAWEI -> "华为/鸿蒙"
        Manufacturer.XIAOMI -> "小米/HyperOS"
        Manufacturer.OPPO -> "OPPO/ColorOS"
        Manufacturer.VIVO -> "VIVO/OriginOS"
        Manufacturer.MEIZU -> "魅族/Flyme"
        Manufacturer.SAMSUNG -> "三星"
        Manufacturer.HONOR -> "荣耀/MagicOS"
        Manufacturer.LENOVO -> "联想/ZUI"
        Manufacturer.GENERIC -> "当前系统"
    }

    /**
     * 获取厂商自启动/后台运行设置说明
     */
    fun getSelfStartGuide(): String = when (detectManufacturer()) {
        Manufacturer.HUAWEI ->
            "请前往「手机管家」→「应用启动管理」→找到「简音」→关闭「自动管理」→" +
            "勾选「自启动」「关联启动」「后台活动」三项"
        Manufacturer.XIAOMI ->
            "请前往「设置」→「应用设置」→「应用管理」→「简音」→「省电策略」→" +
            "选择「无限制」；同时在「自启动管理」中允许简音自启动"
        Manufacturer.OPPO ->
            "请前往「设置」→「应用」→「应用管理」→「简音」→「耗电保护」→" +
            "选择「允许后台运行」"
        Manufacturer.VIVO ->
            "请前往「设置」→「电池」→「后台高耗电」→允许「简音」；" +
            "同时前往「i管家」→「应用管理」→「权限管理」→「自启动」→允许「简音」"
        Manufacturer.MEIZU ->
            "请前往「手机管家」→「权限管理」→「后台管理」→选择「简音」→" +
            "「允许后台运行」"
        Manufacturer.SAMSUNG ->
            "请前往「设置」→「设备维护」→「电池」→「未监视的应用程序」→" +
            "添加「简音」；同时在「应用程序」→「简音」→「电池」→「优化电池使用量」关闭"
        Manufacturer.HONOR ->
            "请前往「手机管家」→「应用启动管理」→找到「简音」→关闭「自动管理」→" +
            "勾选「自启动」「关联启动」「后台活动」三项"
        Manufacturer.LENOVO ->
            "请前往「设置」→「应用管理」→「简音」→「自启动」→允许自启动；" +
            "同时在「电池管理」中关闭对简音的优化"
        Manufacturer.GENERIC ->
            "请前往「设置」→「应用」→「简音」→「电池」→选择「不限制」或「允许后台运行」"
    }

    /**
     * 尝试跳转至厂商自启动/后台设置页面
     *
     * @return true 如果成功跳转，false 如果没有匹配的厂商页面
     */
    fun openAutoStartSettings(context: Context): Boolean {
        return try {
            val intent = when (detectManufacturer()) {
                Manufacturer.HUAWEI -> {
                    // 华为：跳转应用启动管理
                    Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                Manufacturer.HONOR -> {
                    // 荣耀：同华为
                    Intent().apply {
                        component = ComponentName(
                            "com.hihonor.systemmanager",
                            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                Manufacturer.XIAOMI -> {
                    // 小米：跳转自启动管理
                    Intent().apply {
                        component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                Manufacturer.OPPO -> {
                    // OPPO：跳转应用详情页（电池设置更可靠）
                    openAppDetailSettings(context)
                    return true
                }
                Manufacturer.VIVO -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    }
                }
                Manufacturer.MEIZU -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.meizu.safe",
                            "com.meizu.safe.security.AppSecActivity"
                        )
                    }
                }
                Manufacturer.SAMSUNG -> {
                    // 跳转电池优化白名单
                    openBatteryOptimizationSettings(context)
                    return true
                }
                Manufacturer.LENOVO,
                Manufacturer.GENERIC -> {
                    openAppDetailSettings(context)
                    return true
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "跳转厂商自启动设置成功: ${getManufacturerName()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "跳转厂商设置失败: ${e.message}，回退到应用详情", e)
            // 失败时回退到通用应用详情页
            openAppDetailSettings(context)
            true
        }
    }

    /**
     * 打开应用的系统详情页（通用兜底）
     */
    fun openAppDetailSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开应用详情失败", e)
        }
    }

    /**
     * 打开电池优化设置（Android 6.0+）
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开电池优化设置失败", e)
            openAppDetailSettings(context)
        }
    }

    /**
     * 完整保活引导：一键尝试引导用户完成所有必要设置
     *
     * 调用顺序：厂商自启动 → 电池优化 → 弹提示
     */
    fun guideUserForKeepAlive(context: Context) {
        if (!openAutoStartSettings(context)) {
            // 厂商跳转失败，打开电池优化
            openBatteryOptimizationSettings(context)
        }
    }
}
