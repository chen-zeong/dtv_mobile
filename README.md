# <p align="center"><img src="screenshots/icon.png" width="96" height="96" alt="DTV Mobile" /></p>
## <p align="center">DTV Mobile</p>

<p align="center">
  Kotlin Multiplatform + Compose Multiplatform 项目（Android 优先），用于聚合内容浏览与播放体验。
</p>

> 这是 DTV 项目的 Android 版本；桌面端请看：https://github.com/chen-zeong/dtv  
> 快速跳转：[打赏](#打赏)

---

## 功能

- 支持平台：斗鱼 / 虎牙 / 抖音 / B站（直播）
- 分区浏览：按平台分类浏览直播列表，支持订阅常用分区（快速入口）
- 关注管理：一键关注/取消关注；首页支持置顶与长按拖拽排序（区分开播/未开播）
- 搜索：按平台搜索主播/直播间；B站支持登录/退出（抓取 Cookie，用于部分接口）
- 播放：基于 Android Media3（ExoPlayer）播放；全屏/横竖屏适配；清晰度/线路选择（平台能力不同）
- 弹幕：实时弹幕展示；关键词屏蔽；字号/透明度/显示区域可调
- 同步：局域网共享/导入（mDNS 发现、手动输入、扫码导入），增量同步关注、分区订阅、屏蔽词
- 主题：浅色 / 深色 / 跟随系统

> 具体功能以代码实现为准。

## 说明

- 本项目仅用于学习与技术交流
- 播放内容与相关数据来自第三方平台接口，可能随平台变更而失效

## 目录结构

- `androidApp/`：Android 应用（入口、Manifest、资源、签名等）
- `shared/`：KMP 共享模块（业务状态、UI、平台适配）
- `desktopApp/`：桌面端（如启用）

## 快速开始（Android）

### 环境要求

- Android Studio（建议使用较新版本）
- JDK 17
- Android SDK（`compileSdk = 36`）

### 运行

```bash
./gradlew :androidApp:installDebug
```

或直接在 Android Studio 里选择 `androidApp` 运行。

### 签名（可选）

仓库默认忽略本地签名文件：`androidApp/keystore.properties`。

如果你需要本地打包 Release：

1. 复制 `androidApp/keystore.properties.example` 为 `androidApp/keystore.properties`
2. 填入自己的 keystore 路径与密码（该文件不会被提交）

## 截图

截图位于 `screenshots/`：

| 首页                                                        | 播放                                                          | 分区（斗鱼）                                                   |
| --------------------------------------------------------- | ----------------------------------------------------------- | ---------------------------------------------------------- |
| <img src="screenshots/home.jpeg" width="240" alt="首页" /> | <img src="screenshots/player.jpeg" width="240" alt="播放" /> | <img src="screenshots/douyu.jpeg" width="240" alt="斗鱼" /> |

| 共享/导入                                                     | B站                                                     |
| ---------------------------------------------------------- | ------------------------------------------------------ |
| <img src="screenshots/share.jpeg" width="240" alt="共享" /> | <img src="screenshots/b.jpeg" width="240" alt="B站" /> |

## Roadmap（想法）

- 更完善的播放控制与错误提示
- 更清晰的模块边界与可测试性
- 统一的日志与崩溃收集（可选）

## 贡献

欢迎提 Issue / PR。提交前建议先本地运行：

```bash
./gradlew build
```

## 打赏

如果这个项目对你有帮助，欢迎打赏支持：

<p>
  <img src="screenshots/wechat.jpg" width="260" alt="微信打赏码" />
</p>
