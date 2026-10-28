# DeepLine 2.2.0 — iOS “liquid glass” redesign

Android package `com.deepline.privateapp` · versionName `2.2.0` (versionCode 15)

## What changed in 2.2.0
**Look & feel**
- New glass design system (`Glass.kt`): frosted translucent surfaces with a specular rim, silver “water” wallpaper (or your own photo from Settings), fading list edges, edge-to-edge layout.
- Chat page rebuilt after the reference: avatar + `···` capsule, “记忆自动总结已开启” pill, per-message avatars, glass capsule bubbles, `Nero ✓ 思考摘要 ⌃` header, `model ✦ tokens ✦ time` footer, glass composer (`+` · 说点什么 · expand · call/send).
- Home page rebuilt after the reference: 编辑 / 消息 / ⊕, search, 私聊·群聊 switch, conversation rows with verified badge, time and unread count, 4-tab bar **消息 · 动态 · 增加AI · 我的**.
- Safe on all devices: status/navigation-bar insets, keyboard (IME) insets, display cutouts, content width capped on tablets, font scale capped (0.85–1.15×), orientation changes no longer reset the app.

**Features**
- **动态** tab: post text/photo, like (toggle), comment, delete own posts; AI friends like/comment/reply, and can post on their own (random 30–60 min while the app runs) or on demand (“让 AI 发一条”).
- **增加AI** tab: import ZIP / create a character / create a group, plus your AI list.
- Bounce animations everywhere (press squish, springy entrances, tab pop, like pop, segmented thumb).
- New synthesized sound effects (send / receive / pop / success) in `res/raw`.
- Red envelopes and money transfers are kept (card redesigned; it no longer gets clipped by the bubble shape).
- “思考摘要” and token counts are shown only when the provider actually returns them (DeepSeek reasoning when 深度思考 is on; `usage` from the API).

**Bug fixes**
- New dynamics overwrote the newest existing one (`JSONArray.put(0, …)` replaces) → now prepended.
- Auto-post replaced the whole data object while chats were in flight (could lose replies) and never refreshed the UI.
- Whole chat JSON was written to disk every second by the coin timer → now every 30 s.
- Images were decoded at full size on the main thread → async, down-sampled, cached, EXIF-rotated.
- Edit button on the message list did nothing → real edit mode (delete with confirmation, pin).
- Import/export status messages were never visible → toast.
- System Back now leaves chat/editors instead of closing the app.
- Like button could be spammed → toggle.
- Several settings had no effect (bubble colours/shape/background image, home title, input hint) → wired up.
- Removed unused ML Kit dependency.

## Build
Open `android/` in Android Studio, or use the included GitHub Actions workflow (`gradle assembleDebug`).

## Verification status
**This archive was written without an Android SDK and has NOT been compiled or run on a device.** Please build it and report any compile error or visual glitch.
