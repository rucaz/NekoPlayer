# NekoPlayer 产品需求文档 (PRD)

> 版本: v1.0.0  
> 日期: 2026-04-08  
> 状态: 已完成开发，待集成测试

---

## 1. 产品概述

### 1.1 产品定位
NekoPlayer 是一款面向二次元音乐爱好者的跨平台音乐播放器，主打 **Bilibili 音源 + 本地歌单管理**，兼顾咪咕音乐作为补充音源。

### 1.2 目标用户
- 核心用户：B站音乐区活跃用户，习惯在B站听歌
- 扩展用户：需要简洁本地歌单管理的音乐爱好者
- 技术用户：对 KMP 跨平台技术感兴趣的开发者

### 1.3 核心价值主张
| 维度 | 价值 |
|------|------|
| 音源 | 无需切换App，B站音乐一键播放 |
| 管理 | 本地歌单，数据自主可控 |
| 体验 | 全局MiniPlayer，无缝切换 |
| 技术 | Kotlin Multiplatform，一套代码双端运行 |

---

## 2. 功能需求

### 2.1 功能矩阵

| 功能模块 | 功能点 | 优先级 | 状态 |
|---------|--------|--------|------|
| **音源接入** | Bilibili 搜索/播放 | P0 | ✅ 已完成 |
| | 咪咕音乐搜索/播放 | P0 | ✅ 已完成 |
| | B站扫码登录 | P1 | ✅ 已完成 |
| **搜索** | 关键词搜索 | P0 | ✅ 已完成 |
| | 搜索结果来源标记 | P1 | ✅ 已完成 |
| | 搜索状态保持 | P1 | ✅ 已完成 |
| | 长按菜单（加入歌单/下一首） | P1 | ✅ 已完成 |
| **歌单** | 歌单CRUD | P0 | ✅ 已完成 |
| | 歌单封面生成 | P1 | ✅ 已完成 |
| | 歌曲添加到歌单 | P0 | ✅ 已完成 |
| | 从歌单移除歌曲 | P1 | ✅ 已完成 |
| **播放** | 基础播放控制 | P0 | ✅ 已完成 |
| | 播放队列管理 | P0 | ✅ 已完成 |
| | 上一首/下一首 | P0 | ✅ 已完成 |
| | 播放模式（顺序/随机/循环） | P1 | ✅ 已完成 |
| | MiniPlayer | P0 | ✅ 已完成 |
| | NowPlaying全屏 | P0 | ✅ 已完成 |
| | 播放进度拖动 | P0 | ✅ 已完成 |
| | 后台播放 | P1 | ✅ 已完成 |

### 2.2 详细功能规格

#### 2.2.1 音源接入

**Bilibili 音源**
- 搜索接口：`https://api.bilibili.com/x/web-interface/search/type`
- 播放链接获取：需登录后调用音频接口
- 登录方式：Web扫码登录，Cookie持久化

**咪咕音乐音源**
- 搜索接口：咪咕开放搜索API
- 免登录即可播放
- 作为B站音源的补充

#### 2.2.2 搜索功能

**搜索流程**
```
用户输入 → 同时调用B站+咪咕API → 合并结果 → 展示列表
```

**来源标记**
| 来源 | 颜色 | 色值 |
|------|------|------|
| bilibili | 粉红 | `#FFFF69B4` |
| migu | 粉蓝 | `#FF00D4FF` |

**状态保持**
- 搜索结果缓存到 ViewModel
- 返回搜索页时恢复列表和滚动位置

**长按菜单**
- 触发：长按400ms
- 选项：
  1. 添加到歌单 → 弹出歌单选择器
  2. 下一首播放 → 调用 QueueManager.addToQueueNext()

#### 2.2.3 歌单系统

**数据模型**
```kotlin
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val coverUrl: String? // 首歌曲封面或渐变背景
)

data class PlaylistSong(
    val playlistId: Long,
    val songId: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val source: String, // "bilibili" | "migu"
    val addedAt: Long
)
```

**数据库表结构**
```sql
-- Playlist.sq
CREATE TABLE playlist (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    cover_url TEXT
);

-- PlaylistSong.sq
CREATE TABLE playlist_song (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    playlist_id INTEGER NOT NULL,
    song_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    cover_url TEXT,
    source TEXT NOT NULL,
    added_at INTEGER NOT NULL,
    UNIQUE(playlist_id, song_id)
);
```

**封面生成策略**
1. 有歌曲时 → 使用第一首歌的封面
2. 无歌曲时 → 渐变色背景（随机种子基于歌单名）

#### 2.2.4 播放系统

**播放队列 (QueueManager)**
```kotlin
interface QueueManager {
    // 播放队列
    fun playQueue(songs: List<Song>, startIndex: Int = 0)
    val currentQueue: StateFlow<List<Song>>
    val currentIndex: StateFlow<Int>
    
    // 导航
    fun playNext()
    fun playPrevious()
    fun addToQueueNext(song: Song)
    
    // 播放模式
    val playMode: StateFlow<PlayMode>
    fun setPlayMode(mode: PlayMode)
}

enum class PlayMode {
    SEQUENTIAL, // 顺序
    SHUFFLE,    // 随机
    REPEAT_ONE  // 单曲循环
}
```

**播放控制**
| 操作 | 行为 |
|------|------|
| 播放歌曲 | 获取播放链接 → 初始化播放器 → 更新队列 |
| 下一首 | 按模式计算下一索引 → 播放 |
| 上一首 | 当前进度>3s则重播，否则上一首 |
| 进度拖动 | 直接 seekTo 指定位置 |

**播放模式逻辑**
- **顺序**: `(currentIndex + 1) % queue.size`
- **随机**: 随机选择未播放过的索引，全部播放后重置
- **单曲**: 保持当前索引，循环播放

#### 2.2.5 MiniPlayer

**触发条件**
- 队列中有歌曲时自动显示
- 高度：64dp
- 位置：屏幕底部

**交互**
| 操作 | 响应 |
|------|------|
| 点击播放条 | 展开 NowPlayingScreen |
| 播放/暂停按钮 | 切换播放状态 |
| 下一首按钮 | playNext() |

**视觉**
- 封面：48dp 圆角
- 进度条：底部 2dp 高度细线
- 背景：毛玻璃效果

---

## 3. 非功能需求

### 3.1 性能指标

| 指标 | 目标值 |
|------|--------|
| 冷启动时间 | < 2s |
| 搜索响应时间 | < 1s (首屏结果) |
| 播放首帧时间 | < 500ms |
| 歌单列表加载 | < 200ms |
| 内存占用 | < 100MB (后台) |

### 3.2 兼容性

| 平台 | 最低版本 | 备注 |
|------|---------|------|
| Android | API 26 (8.0) | 需支持通知渠道 |
| iOS | 14.0 | 支持后台音频 |

### 3.3 稳定性
- 崩溃率 < 0.1%
- ANR率 < 0.05%
- 播放中断率 < 1%

---

## 4. 数据埋点

### 4.1 核心指标

| 事件 | 用途 |
|------|------|
| search | 搜索频次、热门关键词 |
| play | 播放成功/失败率 |
| playlist_create | 歌单创建转化率 |
| add_to_playlist | 歌曲收藏率 |
| play_mode_switch | 播放模式偏好 |

---

## 5. 迭代计划

### v1.0.0 (当前)
- 基础搜索播放
- 本地歌单管理
- MiniPlayer + 播放队列

### v1.1.0 (规划)
- [ ] 歌词显示
- [ ] 睡眠定时
- [ ] 均衡器
- [ ] 导入/导出歌单

### v1.2.0 (规划)
- [ ] 收藏视频（B站视频转音频）
- [ ] 播放历史
- [ ] 智能推荐
- [ ] 桌面小部件

---

## 6. 附录

### 6.1 技术栈
- Kotlin Multiplatform Mobile (KMP)
- Compose Multiplatform
- SQLDelight (数据库)
- Ktor (网络)
- Koin (依赖注入)
- Voyager (导航)
- ExoPlayer (Android播放)
- AVPlayer (iOS播放)

### 6.2 项目结构
```
NekoPlayer/
├── shared/
│   ├── commonMain/
│   │   ├── data/
│   │   │   ├── api/         # Bili/Migu API
│   │   │   ├── repository/  # 仓库层
│   │   │   └── model/       # 数据模型
│   │   ├── player/          # 播放器 + 队列管理
│   │   ├── database/        # SQLDelight
│   │   ├── ui/
│   │   │   ├── screens/     # 页面
│   │   │   ├── components/  # 组件
│   │   │   └── viewmodel/   # VM
│   │   └── di/              # 依赖注入
│   ├── androidMain/         # Android特有
│   └── iosMain/             # iOS特有
├── androidApp/              # Android入口
└── iosApp/                  # iOS入口
```

### 6.3 相关文档
- [交互设计文档](./Interaction_Design.md)
- [用户使用手册](../README_USER.md)
- [开发作战计划](../BATTLE_PLAN.md)
