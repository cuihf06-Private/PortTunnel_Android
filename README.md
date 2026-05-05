# PortTunnel Android

SSH 端口转发工具的 Android 版本，基于原 Python/FastAPI 工具重构而来。

## 功能

- **配置管理**：通过 Room 数据库本地保存 SSH 配置（别名、主机、端口、用户名、端口映射）
- **SSH 端口转发**：使用 JSch 库建立 SSH 隧道，将远程端口映射到设备本地端口
- **前台服务**：隧道运行在 Android 前台服务中，切换 App 后保持连接
- **状态实时更新**：Compose + StateFlow 实时显示每条隧道的连接状态
- **密码不保存**：每次连接时输入 SSH 密码，不持久化存储

## 架构

```
MVVM + Foreground Service
├── data/          Room 数据库（Profile 实体）
├── service/       TunnelService（管理 JSch Session）
├── viewmodel/     MainViewModel（连接 UI 与 Service）
└── ui/screens/    HomeScreen（Jetpack Compose）
```

## 构建

1. 用 Android Studio 打开此目录
2. 等待 Gradle 同步
3. 运行到设备或模拟器（API 26+）

## 依赖

| 库 | 用途 |
|---|---|
| `com.jcraft:jsch:0.1.55` | SSH 隧道（纯 Java，兼容 Android） |
| `androidx.room` | 本地配置存储 |
| `Jetpack Compose` | 现代 UI |
| `Kotlin Coroutines` | 异步 SSH 连接 |

## 权限

- `INTERNET` — 建立 SSH 连接
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` — 后台保持隧道
- `POST_NOTIFICATIONS` — 显示隧道状态通知（Android 13+）
