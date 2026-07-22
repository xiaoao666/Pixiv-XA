# Pixiv XA · Art Atlas

一个面向 Android 的第三方 Pixiv 客户端，提供推荐、搜索、排行榜、R18、关注动态、评论、收藏、下载与浏览记录，并采用粉紫星图风格的二次元 UI。

## 当前功能

- Pixiv OAuth 2.0 + PKCE 登录、refresh token 自动刷新
- 推荐流、最新插画 / 漫画 / 小说、关注画师动态
- 独立搜索页：作品 / 标签、画师搜索、作品 ID / 画师 ID 直达
- 独立排行榜：日期、插画 / 漫画 / 小说及多种榜单模式
- R18 / R18G 入口与今日、周榜、男女、AI 等分类
- 热门标签首图卡片与点击搜索
- 作品详情：完整比例显示、多 P 左右滑动、全屏查看、相关推荐持续加载
- 作者头像、画师主页、关注 / 取消关注
- 评论、评论头像、回复、取消回复、Pixiv 贴图与失败重试
- Pixiv 云端收藏同步，公开 / 私密收藏聚合，本地状态持久化
- 下载队列、进度展示、浏览记录
- 国内直连模式：Cronet QUIC、安全 DNS、作品图片镜像；评论贴图保留官方静态域名
- 二次元 adaptive app icon、昼夜主题、极光背景与过渡动画

## 构建

环境：Android Studio / JDK 11+ / Android SDK 36。

```powershell
.\gradlew.bat test assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 网络说明

Pixiv API 属于非公开客户端接口，字段、登录策略和风控可能变化。网络实现集中在 `app/src/main/java/com/xa/pixiv/data/`，便于后续维护。国内直连并不承诺所有运营商和地区永久可用；若网络环境变化，可在“我的 → 网络模式 / 图片加速源”中切换。

## 版权与致谢

作品内容、图片与作者资料版权归 Pixiv 及各创作者所有。功能设计与部分 API 集成思路参考 [CeuiLiSA/Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft)，详见 [NOTICE-SHAFT.md](NOTICE-SHAFT.md) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

仓库中的本地演示图仅用于界面开发和验收；公开分发前应确认素材授权或替换为自有素材。
