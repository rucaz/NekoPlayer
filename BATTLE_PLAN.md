# NekoPlayer 女武神作战计划

## 作战目标
4个功能需求，分4条战线并行推进，最后集成

---

## 女武神-01：数据层 (Phase 1)
**负责**: SQLDelight 数据库 + Repository + QueueManager
**工期**: 1.5 天
**关键交付**:
- `Playlist.sq` 歌单表定义
- `PlaylistSong.sq` 歌单歌曲关联表
- `PlaylistRepository.kt` 歌单仓库接口
- `QueueManager.kt` 播放队列管理器（内存中）

**接口契约**:
```kotlin
// QueueManager 必须暴露
fun playQueue(songs: List<Song>, startIndex: Int = 0)
fun playNext()
fun playPrevious()
fun addToQueueNext(song: Song)  // 插入到当前索引+1
val currentQueue: StateFlow<List<Song>>
val currentIndex: StateFlow<Int>
```

---

## 女武神-02：MiniPlayer (Phase 2)
**负责**: 全局播放条 + AudioPlayer 扩展
**工期**: 1.5 天
**关键交付**:
- `AudioPlayer.kt` 扩展上一首/下一首/队列方法
- `MiniPlayer.kt` 底部播放条组件
- `App.kt` 集成 MiniPlayer 到全局布局

**依赖**: 需要 QueueManager 接口完成后联调

**UI规范**:
- 高度: 64dp
- 位置: 底部固定
- 内容: 封面(48dp) + 歌曲信息 + 播放/下一首按钮
- 底部进度条: 2dp 高度细线
- 点击展开 NowPlayingScreen

---

## 女武神-03：歌单UI (Phase 3)
**负责**: 歌单列表页 + 详情页 + 弹窗
**工期**: 1.5 天
**关键交付**:
- `PlaylistListScreen.kt` 歌单网格列表
- `PlaylistDetailScreen.kt` 歌单详情页
- `CreatePlaylistDialog.kt` 新建歌单弹窗
- `AddToPlaylistDialog.kt` 添加到歌单选择器

**依赖**: 需要 PlaylistRepository 接口

**UI规范**:
- 歌单封面: 圆角 12dp，支持渐变背景或首歌曲封面
- 网格布局: 2列，间距 12dp
- 空状态: 提示文字 + 快速创建按钮

---

## 女武神-04：搜索优化 (Phase 4)
**负责**: 来源标记 + 搜索状态保持 + 长按菜单
**工期**: 1 天
**关键交付**:
- `SearchScreen.kt` 添加来源标签（B站粉红/Migu粉蓝）
- 搜索结果状态保持（ViewModel 优化）
- 长按弹出 BottomSheet 菜单
- 集成"加入歌单"和"下一首播放"

**颜色规范**:
```kotlin
val BilibiliPink = Color(0xFFFF69B4)   // B站来源
val MiguCyan = Color(0xFF00D4FF)       // 咪咕来源
```

**依赖**: 需要 QueueManager.addToQueueNext() 和 AddToPlaylistDialog

---

## 集成顺序

```
Day 1: 女武神-01 数据层（上午启动，下午完成接口）
       ↓
Day 1~2: 女武神-02、03、04 并行开发（基于 mock 接口）
         ↓
Day 2~3: 联调集成，替换 mock 为真实 Repository
```

---

## 状态追踪

- [x] Phase 1: 数据层 ✅ 完成
- [x] Phase 2: MiniPlayer ✅ 完成
- [x] Phase 3: 歌单UI ✅ 完成
- [x] Phase 4: 搜索优化 ✅ 完成
- [ ] 集成测试
