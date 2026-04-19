# 简音

<div align="center">

<img src="images/logo.png" width="200" height="200" alt="简音 Logo">

**一个基于 Meting API 的现代化音乐播放器**

[![Android API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat-square)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg?style=flat-square)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL%203.0-blue.svg?style=flat-square)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/qianqianhhh2/jianyin?style=flat-square)](https://github.com/qianqianhhh2/jianyin/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/qianqianhhh2/jianyin?style=flat-square)](https://github.com/qianqianhhh2/jianyin/network/members)

[下载 APK](https://github.com/qianqianhhh2/jianyin/releases) · [报告 Bug](https://github.com/qianqianhhh2/jianyin/issues) · [功能建议](https://github.com/qianqianhhh2/jianyin/issues)

</div>

***

## 项目简介

简音是一款使用 Kotlin 和 Jetpack Compose 开发的现代化 Android 音乐播放器，基于 Meting API 提供音乐服务。采用 MVVM 架构，结合 Material Design 3 设计规范，为用户提供流畅、美观的音乐体验。

## 核心特性

- 多平台音乐支持 - 集成网易云、QQ音乐等主流音乐平台
- 现代化 UI - 基于 Material Design 3 和 Jetpack Compose
- 后台播放 - 支持锁屏控制和通知栏控制
- 播放列表管理 - 创建、编辑、同步播放列表
- 音乐下载 - 支持离线播放
- 毛玻璃效果 - 精美的视觉效果
- 深色模式 - 完整的深色主题支持
- 数据备份 - 支持播放列表和设置备份

## 技术栈

### 核心框架

- **Kotlin** - 现代化编程语言
- **Jetpack Compose** - 声明式 UI 框架
- **Material Design 3** - Google 最新设计系统

### 架构模式

- **MVVM** - Model-View-ViewModel 架构
- **Repository** - 数据仓库模式
- **DataStore** - 数据持久化

### 核心库

- **Media3 ExoPlayer** - 媒体播放引擎
- **MediaSession** - 媒体会话管理
- **Retrofit** - 网络请求
- **Gson** - JSON 解析
- **OkHttp** - HTTP 客户端
- **Coil** - 图片加载
- **WorkManager** - 后台任务调度

### UI 效果

- **Haze** - 毛玻璃模糊效果
- **Material You** - 动态取色

### 其他库

- **JAudiotagger** - 音频文件元数据处理
- **Kotlin Coroutines** - 异步编程
- **AndroidX Security** - 安全加密
- **Accompanist Permissions** - 权限处理

## 项目结构

```
jianyin/
├── app/                                    # 主应用模块
│   ├── src/main/
│   │   ├── kotlin/com/qian/jianyin/        # 主代码目录
│   │   │   ├── MainActivity.kt              # 主 Activity
│   │   │   ├── MusicViewModel.kt            # 音乐视图模型
│   │   │   ├── HomeScreenViewModel.kt       # 首页视图模型
│   │   │   ├── DataModels.kt                # 数据模型
│   │   │   ├── VersionChecker.kt            # 版本检查器
│   │   │   ├── VersionUpdateDialog.kt       # 版本更新弹窗
│   │   │   ├── MyMusicScreenV2.kt           # 我的音乐界面
│   │   │   ├── HomeScreen.kt                # 主界面
│   │   │   ├── SearchScreen.kt              # 搜索界面
│   │   │   ├── MiniPlayer.kt                # 迷你播放器
│   │   │   ├── MyLibraryScreen.kt           # 我的音乐库
│   │   │   ├── OnboardingScreen.kt          # 引导页
│   │   │   ├── BiliWebLoginActivity.kt      # B站登录界面
│   │   │   ├── MusicPlayerManager.kt        # 播放管理器
│   │   │   ├── PlaybackService.kt           # 后台服务
│   │   │   ├── MediaSessionManager.kt       # 会话管理
│   │   │   ├── PlayerHolder.kt              # 播放器持有者
│   │   │   ├── PlaybackMode.kt              # 播放模式
│   │   │   ├── DownloadManager.kt           # 下载管理
│   │   │   ├── DownloadProgressDialog.kt    # 下载进度弹窗
│   │   │   ├── DownloadSettingsStore.kt     # 下载设置存储
│   │   │   ├── DownloadStateManager.kt      # 下载状态管理
│   │   │   ├── PlaylistDataStore.kt         # 播放列表存储
│   │   │   ├── PlaylistSyncManager.kt       # 播放列表同步管理
│   │   │   ├── BiliPlaylistSyncManager.kt   # B站播放列表同步管理
│   │   │   ├── BackupManager.kt             # 备份管理
│   │   │   ├── MusicStatsManager.kt         # 统计管理
│   │   │   ├── LocalMusicManager.kt         # 本地音乐管理
│   │   │   ├── ImportProgressDialog.kt      # 导入进度弹窗
│   │   │   ├── ImportStateManager.kt        # 导入状态管理
│   │   │   ├── SongCustomDataStore.kt       # 歌曲自定义数据存储
│   │   │   ├── BiliPlayerHelper.kt          # B站播放器助手
│   │   │   ├── HitokotoManager.kt           # 一言管理
│   │   │   ├── KeepAliveWorker.kt           # 保活工作器
│   │   │   ├── PermissionManager.kt         # 权限管理
│   │   │   ├── PlaylistItemV6.kt            # 播放列表项组件
│   │   │   └── MaterialUtils.kt             # Material 工具
│   │   ├── res/                             # 资源文件
│   │   │   ├── drawable/                    # 图片资源
│   │   │   ├── drawable-v24/                # 高版本图片资源
│   │   │   ├── mipmap-anydpi-v26/           # 自适应图标
│   │   │   ├── mipmap-hdpi/                 # 高密度图标
│   │   │   ├── mipmap-mdpi/                 # 中密度图标
│   │   │   ├── mipmap-xhdpi/                # 超高密度图标
│   │   │   ├── mipmap-xxhdpi/               # 超超高密度图标
│   │   │   ├── mipmap-xxxhdpi/              # 超超超高密度图标
│   │   │   ├── values/                      # 默认值
│   │   │   ├── values-night/                # 夜间模式值
│   │   │   └── xml/                         # XML 配置文件
│   │   └── AndroidManifest.xml              # 应用清单文件
│   ├── build.gradle                         # 模块构建配置
│   └── proguard-rules.pro                   # 混淆规则
├── bili-api/                                # Bilibili API 模块
│   ├── src/main/java/moe/ouom/biliapi/      # Bili API 代码
│   │   ├── BiliApiHelper.kt                 # Bili API 助手
│   │   ├── BiliWebLoginHelper.kt            # B站网页登录助手
│   │   ├── data/auth/                       # 认证数据
│   │   │   └── BiliCookieRepository.kt      # B站 Cookie 仓库
│   │   ├── data/platform/                   # 平台数据
│   │   │   └── BiliAudioSelector.kt         # B站音频选择器
│   │   └── util/                            # 工具类
│   │       └── BiliLogger.kt                # B站日志工具
│   ├── build.gradle.kts                     # 模块构建配置
│   ├── consumer-rules.pro                   # 消费者规则
│   └── README.md                            # 模块说明
├── gradle/                                  # Gradle 配置
│   ├── wrapper/                             # Gradle 包装器
│   │   ├── gradle-wrapper.jar              # Gradle 包装器 JAR
│   │   └── gradle-wrapper.properties       # Gradle 包装器配置
│   └── libs.versions.toml                   # 依赖版本管理
├── images/                                  # 图片资源
│   └── logo.png                             # 应用 Logo
├── .gitignore                               # Git 忽略文件
├── LICENSE                                  # GPL 3.0 许可证
├── README.md                                # 项目说明
├── build.gradle                             # 根构建配置
├── gradle.properties                        # Gradle 属性
├── gradlew                                  # Gradle 执行脚本（Linux/Mac）
├── gradlew.bat                              # Gradle 执行脚本（Windows）
└── settings.gradle                          # 项目设置
```

## 快速开始

### 环境要求

- **Android Studio** - Hedgehog (2023.1.1) 或更高版本
- **Gradle** - 9.0 或更高版本
- **JDK** - 17 或更高版本
- **Android SDK** - API Level 21 或更高版本

### 克隆项目

```bash
git clone https://github.com/qianqianhhh2/jianyin.git
cd jianyin
```

### 构建项目

1. 使用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 按钮或使用快捷键 `Shift + F10`
注意！ 你需要手动配置签名，否则会报错。
### 命令行构建

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

## 功能使用

### 播放音乐

- 在首页浏览推荐音乐
- 使用搜索功能查找歌曲
- 点击播放按钮开始播放
- 使用迷你播放器控制播放

### 管理播放列表

- 创建自定义播放列表
- 添加或删除歌曲
- 同步播放列表到云端

### 下载音乐

- 长按歌曲选择下载
- 在下载管理中查看进度
- 离线播放已下载音乐

### 备份数据

- 在设置中选择备份
- 选择备份内容（播放列表、设置等）
- 恢复数据时选择备份文件

## 开发指南

### 架构设计

项目采用 MVVM + Repository 架构：

- **View** - Jetpack Compose UI 组件
- **ViewModel** - 管理界面状态和业务逻辑
- **Repository** - 数据层抽象，统一数据来源
- **DataStore** - 本地数据持久化

### 代码规范

- 遵循 Kotlin 官方代码风格
- 使用 Material Design 3 组件
- 注释使用中文
- 函数命名使用驼峰命名法

### 提交规范

```
feat: 新功能
fix: 修复 bug
docs: 文档更新
style: 代码格式调整
refactor: 重构代码
test: 测试相关
chore: 构建/工具相关
```

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

### 贡献者

暂无贡献者，当然你可以参与贡献。

## 作者

**谦谦TWT**

- Bilibili: [独角大盗取的](https://space.bilibili.com/)
- QQ交流群: 1082723263
- GitHub: [@qianqianhhh2](https://github.com/qianqianhhh2)

## 许可证

本项目采用 GPL-3.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 致谢

感谢以下开源项目：

### 核心框架

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代声明式 UI
- [Material Design 3](https://m3.material.io/) - Google 设计系统
- [AndroidX Media3](https://developer.android.com/media/media3) - 媒体播放框架
- [ExoPlayer](https://github.com/google/ExoPlayer) - 强大的媒体播放器

### 网络与数据

- [Retrofit](https://square.github.io/retrofit/) - 类型安全的 HTTP 客户端
- [Gson](https://github.com/google/gson) - JSON 序列化库
- [OkHttp](https://square.github.io/okhttp/) - 高效 HTTP 客户端
- [JSON](https://github.com/stleary/JSON-java) - JSON 处理库

### 图片处理

- [Coil](https://coil-kt.github.io/coil/) - Kotlin 图片加载库

### UI 效果

- [Haze](https://github.com/chrisbanes/haze) - 毛玻璃模糊效果

### 权限处理

- [Accompanist Permissions](https://github.com/google/accompanist) - Compose 权限处理库

### 歌曲数据处理

- [JAudiotagger](https://github.com/ijabz/jaudiotagger) - 音频文件元数据处理库

### 协程

- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) - 异步编程库

### 安全

- [AndroidX Security](https://developer.android.com/jetpack/androidx/releases/security) - 安全加密库

### API 服务

- [Meting API](https://github.com/metowolf/Meting) - 音乐接口服务

### 构建工具

- [Gradle](https://gradle.org/) - 构建自动化工具

### 参考项目

- [NeriPlayer](https://github.com/cwuom/NeriPlayer) - 多平台音视频聚合流媒体播放器

## 联系方式

如有问题或建议，欢迎通过以下方式联系：

- 提交 [Issue](https://github.com/qianqianhhh2/jianyin/issues)
- 加入 QQ 群: 1082723263
- 发送邮件: [联系作者](mailto:2362813794@qq.com)

***

<div align="center">

**如果这个项目对你有帮助，请给个 Star ⭐**

Made with ❤️ by 谦谦TWT

</div>
