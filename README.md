# NekoPlayer 🎵

跨平台音乐播放器 (Kotlin Multiplatform + Compose)

## 支持平台
- Android (手机/平板/TV)
- iOS (iPhone/iPad)

## 音源
- **哔哩哔哩音频** (主) - 二次元音乐，需扫码登录
- **咪咕音乐** (备) - 华语流行，免登录

## 技术栈
- Kotlin Multiplatform Mobile (KMP)
- Compose Multiplatform
- ExoPlayer (Android) / AVPlayer (iOS)
- Ktor (网络请求)
- kotlinx.coroutines (异步)

## 开发环境
- Android Studio Hedgehog+
- Xcode 15+
- macOS (iOS开发必需)

## 项目结构
```
NekoPlayer/
├── shared/              # 共享模块
│   ├── commonMain/      # 通用代码
│   ├── androidMain/     # Android特有
│   └── iosMain/         # iOS特有
├── androidApp/          # Android入口
└── iosApp/              # iOS入口
```

## License
MIT
