package com.deepline.privateapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// ───────────────────────────── Time + preview helpers ─────────────────────────────

internal fun clockText(ts: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** Conversation-list time: today → 16:35, yesterday → 昨天, this year → 10/28, otherwise full date. */
internal fun formatListTime(ts: Long): String {
    if (ts <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    if (sameDay(now, then)) return clockText(ts)
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(yesterday, then)) return "昨天"
    val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) "M/d" else "yyyy/M/d"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(ts))
}

/** Dynamic-feed time: 刚刚 / N 分钟前 / 今天 / 昨天 / 日期. */
internal fun relativeTime(ts: Long): String {
    if (ts <= 0L) return ""
    val diff = System.currentTimeMillis() - ts
    if (diff < 60_000L) return "刚刚"
    if (diff < 3_600_000L) return "${diff / 60_000L} 分钟前"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    if (sameDay(now, then)) return "今天 ${clockText(ts)}"
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(yesterday, then)) return "昨天 ${clockText(ts)}"
    return java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
}

internal fun lastPreview(arr: JSONArray?): Pair<String, Long> {
    if (arr == null) return "" to 0L
    for (i in arr.length() - 1 downTo 0) {
        val m = arr.optJSONObject(i) ?: continue
        val c = m.optString("content")
        if (c == "---" || m.optString("type") == "summary") continue
        val speaker = m.optString("speakerName")
        val text = when {
            m.optString("type") == "payment" ->
                (if (m.optString("paymentType") == "redpacket") "[红包] ¥" else "[转账] ¥") + m.optString("amount")
            c.contains("[[IMAGE:") -> "[图片] " + c.substringBefore("[[IMAGE:").trim()
            c.contains("[[GIF:") -> "[动图]"
            else -> c.replace("\n", " ")
        }.trim()
        val withSpeaker = if (speaker.isNotBlank() && m.optString("role") == "assistant") "$speaker：$text" else text
        return withSpeaker to m.optLong("time", 0L)
    }
    return "" to 0L
}

internal fun unreadCount(arr: JSONArray?): Int {
    var n = 0
    if (arr == null) return 0
    for (i in 0 until arr.length()) {
        val m = arr.optJSONObject(i) ?: continue
        if (m.optString("role") == "assistant" && m.has("read") && !m.optBoolean("read", true) && m.optString("content") != "---") n++
    }
    return n
}

internal fun totalUnread(data: JSONObject): Int {
    val a = data.optJSONArray("personas") ?: return 0
    var n = 0
    for (i in 0 until a.length()) n += unreadCount(a.optJSONObject(i)?.optJSONArray("messages"))
    return n
}

// ───────────────────────────── Shared small widgets ─────────────────────────────

@Composable
fun StatusToast(text: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    LaunchedEffect(text) {
        delay(4200L)
        onDismiss()
    }
    Row(
        modifier
            .padding(horizontal = 20.dp)
            .bounceIn()
            .bounceClick(pressedScale = 0.97f) { onDismiss() }
            .glassSurface(RoundedCornerShape(22.dp), 2f, 8.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, color = Ink, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
fun GlassSearchField(value: String, onChange: (String) -> Unit, hint: String = "搜索消息或用户") {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(46.dp)
            .glassSurface(RoundedCornerShape(23.dp), 1.2f).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = Muted, fontSize = 16.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Default.Close, "清除", tint = Muted, modifier = Modifier.size(20.dp).bounceClick { onChange("") })
        }
    }
}

@Composable
fun GlassSegmented(options: List<String>, selected: String, onSelect: (String) -> Unit, accent: Color) {
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(42.dp)
            .glassSurface(RoundedCornerShape(21.dp), 1.2f)
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbX by animateDpAsState(
            targetValue = segmentWidth * index,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "segment-thumb"
        )
        Box(
            Modifier.offset(x = thumbX).width(segmentWidth).fillMaxHeight().padding(3.dp)
                .clip(RoundedCornerShape(18.dp)).background(accent)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().bounceClick(pressedScale = 0.95f) { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(option, color = if (option == selected) Color.White else Muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ───────────────────────────── Tab bar ─────────────────────────────

@Composable
fun AppTabBar(tab: String, accent: Color, unread: Int, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassBar(edgeTop = true).navigationBarsPadding().height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabItem("消息", tab == "聊天", accent, Icons.Default.ChatBubble, Icons.Outlined.ChatBubbleOutline, unread) { onSelect("聊天") }
        TabItem("动态", tab == "动态", accent, Icons.Default.DynamicFeed, Icons.Outlined.DynamicFeed, 0) { onSelect("动态") }
        TabItem("增加AI", tab == "增加AI", accent, Icons.Default.PersonAdd, Icons.Outlined.PersonAdd, 0) { onSelect("增加AI") }
        TabItem("我的", tab == "我", accent, Icons.Default.Settings, Icons.Outlined.Settings, 0) { onSelect("我") }
    }
}

@Composable
private fun RowScope.TabItem(
    label: String, selected: Boolean, accent: Color,
    selectedIcon: ImageVector, icon: ImageVector, badge: Int, onClick: () -> Unit
) {
    val pop by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab-pop"
    )
    val tint = if (selected) accent else Color(0xFF8A9199)
    Column(
        Modifier.weight(1f).fillMaxHeight().bounceClick(pressedScale = 0.88f) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                if (selected) selectedIcon else icon, null, tint = tint,
                modifier = Modifier.size(27.dp).graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                }
            )
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-5).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(RoundedCornerShape(50)).background(Color(0xFFFF3B30)).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ───────────────────────────── Messages tab ─────────────────────────────

@Composable
fun MessagesTab(
    data: JSONObject, revision: Int, accent: Color,
    search: String, onSearch: (String) -> Unit,
    mode: String, onMode: (String) -> Unit,
    editMode: Boolean,
    onOpenChat: (String) -> Unit, onOpenGroup: (String) -> Unit,
    onEditPersona: (JSONObject) -> Unit, onExport: () -> Unit,
    onTogglePin: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        GlassSearchField(search, onSearch)
        GlassSegmented(listOf("私聊", "群聊"), mode, onMode, accent)
        if (mode == "私聊") {
            val all = data.optJSONArray("personas") ?: JSONArray()
            val list = remember(revision, search, all.length()) {
                val q = search.trim()
                (0 until all.length()).mapNotNull { all.optJSONObject(it) }
                    .filter { p ->
                        q.isBlank() || p.optString("name").contains(q, true) || p.optString("bio").contains(q, true) ||
                            p.optString("persona").contains(q, true) ||
                            (p.optJSONArray("messages")?.let { ms ->
                                (0 until ms.length()).any { ms.optJSONObject(it)?.optString("content")?.contains(q, true) == true }
                            } ?: false)
                    }
                    .sortedByDescending { it.optBoolean("pinned", false) }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (all.length() == 0) EmptyState("还没有聊天对象", "点右上角 + 或「增加AI」创建第一个 AI")
                    else EmptyState("没有找到", "换个关键词试试")
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f, fill = false).padding(horizontal = 14.dp, vertical = 8.dp)
                        .glassSurface(RoundedCornerShape(26.dp), 1.3f, 4.dp)
                ) {
                    itemsIndexed(list, key = { i, p -> p.optString("id").ifBlank { "p$i" } }) { index, p ->
                        PersonaRow(
                            p = p, accent = accent, editMode = editMode, showDivider = index != list.lastIndex,
                            onOpen = { onOpenChat(p.optString("id")) }, onEdit = { onEditPersona(p) },
                            onExport = onExport, onPin = { onTogglePin(p) }, onDelete = { onDelete(p) }
                        )
                    }
                }
            }
        } else {
            val groups = data.optJSONArray("groups") ?: JSONArray()
            val list = remember(revision, groups.length()) { (0 until groups.length()).mapNotNull { groups.optJSONObject(it) } }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f)) { EmptyState("还没有群聊", "到「增加AI」里创建群聊，把 AI 们拉进来") }
            } else {
                LazyColumn(
                    Modifier.weight(1f, fill = false).padding(horizontal = 14.dp, vertical = 8.dp)
                        .glassSurface(RoundedCornerShape(26.dp), 1.3f, 4.dp)
                ) {
                    itemsIndexed(list, key = { i, g -> g.optString("id").ifBlank { "g$i" } }) { index, g ->
                        GroupRow(g, accent, index != list.lastIndex) { onOpenGroup(g.optString("id")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifiedBadge() {
    Icon(Icons.Default.Verified, null, tint = Color(0xFF3B9AF5), modifier = Modifier.size(17.dp))
}

@Composable
private fun RowDivider(modifier: Modifier) {
    Box(modifier.padding(start = 84.dp).fillMaxWidth().height(0.5.dp).background(Color.Black.copy(alpha = 0.08f)))
}

@Composable
private fun PersonaRow(
    p: JSONObject, accent: Color, editMode: Boolean, showDivider: Boolean,
    onOpen: () -> Unit, onEdit: () -> Unit, onExport: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit
) {
    var menuOpen by remember(p.optString("id")) { mutableStateOf(false) }
    val messages = p.optJSONArray("messages")
    val (previewRaw, lastTime) = lastPreview(messages)
    val preview = previewRaw.ifBlank { p.optString("bio").ifBlank { p.optString("persona") } }
    val unread = unreadCount(messages)
    val pinned = p.optBoolean("pinned", false)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .bounceClick(pressedScale = 0.975f, onLongClick = { menuOpen = true }) { if (!editMode) onOpen() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = editMode, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(24.dp).bounceClick { onDelete() }.background(Color(0xFFFF3B30), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Remove, "删除", tint = Color.White, modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.width(12.dp))
                }
            }
            Avatar(p.optString("avatar"), p.optString("name"), 56)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.optString("name"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(5.dp))
                    VerifiedBadge()
                }
                Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned) Icon(Icons.Default.PushPin, null, tint = accent, modifier = Modifier.size(13.dp).padding(end = 2.dp))
                    Text(formatListTime(lastTime), fontSize = 13.sp, color = Muted)
                }
                Spacer(Modifier.height(5.dp))
                if (editMode) {
                    Icon(
                        Icons.Default.PushPin, "置顶", tint = if (pinned) accent else Muted,
                        modifier = Modifier.size(22.dp).bounceClick { onPin() }
                    )
                } else if (unread > 0) {
                    Box(
                        Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                } else {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        if (showDivider) RowDivider(Modifier.align(Alignment.BottomEnd))
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("编辑 AI") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; onEdit() })
            DropdownMenuItem(text = { Text("导出完整数据 ZIP") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = { menuOpen = false; onExport() })
            DropdownMenuItem(text = { Text(if (pinned) "取消置顶" else "置顶 AI") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menuOpen = false; onPin() })
            DropdownMenuItem(text = { Text("删除 AI", color = Color(0xFFD64255)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFD64255)) }, onClick = { menuOpen = false; onDelete() })
        }
    }
}

@Composable
private fun GroupRow(g: JSONObject, accent: Color, showDivider: Boolean, onOpen: () -> Unit) {
    val members = g.optJSONArray("memberIds")?.length() ?: 0
    val (previewRaw, lastTime) = lastPreview(g.optJSONArray("messages"))
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().bounceClick(pressedScale = 0.975f) { onOpen() }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Groups, null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.optString("name", "群聊"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(previewRaw.ifBlank { "$members 位 AI 成员" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatListTime(lastTime), fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(5.dp))
                Text("$members AI", fontSize = 11.sp, color = accent, modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        if (showDivider) RowDivider(Modifier.align(Alignment.BottomEnd))
    }
}

// ───────────────────────────── Dynamic (moments) tab ─────────────────────────────

private data class KeyedPost(val key: String, val post: JSONObject)

@Composable
fun DynamicTab(
    data: JSONObject, revision: Int, accent: Color,
    myName: String, myBio: String, myAvatar: String, cover: String,
    feedText: String, onFeedText: (String) -> Unit,
    postImage: String, onPickPostImage: () -> Unit, onClearPostImage: () -> Unit, onPublish: () -> Unit,
    autoAi: Boolean, onAutoAi: (Boolean) -> Unit, aiReact: Boolean, onAiReact: (Boolean) -> Unit,
    aiPostBusy: Boolean, onAiPostNow: () -> Unit,
    onPickCover: () -> Unit, onLike: (JSONObject) -> Unit, onOpenComments: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit
) {
    val arr = data.optJSONArray("posts") ?: JSONArray()
    val posts = remember(revision, arr.length()) {
        val seen = HashSet<String>()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapIndexed { i, post ->
            var key = post.optString("id").ifBlank { "post$i" }
            while (!seen.add(key)) key += "_"
            KeyedPost(key, post)
        }
    }
    val doneKeys = remember { HashSet<String>().also { set -> posts.forEach { set.add(it.key) } } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "cover") {
            Box(Modifier.fillMaxWidth().height(232.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(190.dp).bounceClick(pressedScale = 0.98f) { onPickCover() }
                        .clip(RoundedCornerShape(28.dp))
                ) {
                    if (cover.isNotBlank()) PickedPhoto(cover, Modifier.fillMaxSize(), rounded = false, maxPx = 1200)
                    else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF8FA6C8), Color(0xFFD7E1F0), Color(0xFFF3F5FA)))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)))))
                    Text(
                        "点击更换封面", fontSize = 11.sp, color = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Row(Modifier.align(Alignment.BottomStart).padding(start = 18.dp), verticalAlignment = Alignment.Bottom) {
                    Avatar(myAvatar, myName, 74)
                    Column(Modifier.padding(start = 12.dp, bottom = 2.dp)) {
                        Text(myName, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink, maxLines = 1)
                        Text(myBio.ifBlank { "记录生活的每一刻" }, fontSize = 12.sp, color = Muted, maxLines = 1)
                    }
                }
            }
        }
        item(key = "ai-controls") {
            Column(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp), 1.2f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingSwitch("AI 自动发动态", "应用运行期间，AI 会按人设不定时发布动态", autoAi, onAutoAi)
                SettingSwitch("AI 点赞 / 评论", "你发布动态后，AI 会按人设决定是否点赞、评论", aiReact, onAiReact)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("想马上看到效果？", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    Row(
                        Modifier.bounceClick(enabled = !aiPostBusy) { onAiPostNow() }
                            .clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.13f))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (aiPostBusy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = accent)
                        else Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (aiPostBusy) "AI 正在写…" else "让 AI 发一条", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
        item(key = "composer") {
            Column(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(26.dp), 1.25f, 4.dp).animateContentSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Avatar(myAvatar, myName, 42)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).padding(top = 10.dp).heightIn(min = 52.dp)) {
                        if (feedText.isEmpty()) Text("今天有什么新鲜事？", color = Muted, fontSize = 16.sp)
                        BasicTextField(
                            value = feedText, onValueChange = onFeedText,
                            textStyle = TextStyle(color = Ink, fontSize = 16.sp, lineHeight = 22.sp),
                            cursorBrush = SolidColor(accent), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (postImage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth()) {
                        PickedPhoto(postImage, Modifier.fillMaxWidth().height(170.dp), maxPx = 1000)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).bounceClick { onClearPostImage() }
                                .clip(CircleShape).background(Color.Black.copy(alpha = 0.40f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Close, "移除图片", tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.bounceClick { onPickPostImage() }.clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, null, tint = Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("图片", color = Muted, fontSize = 13.sp)
                    }
                    val canPublish = feedText.isNotBlank() || postImage.isNotBlank()
                    val bg by animateFloatAsState(if (canPublish) 1f else 0.35f, label = "publish-alpha")
                    Row(
                        Modifier.bounceClick(enabled = canPublish) { onPublish() }.clip(RoundedCornerShape(20.dp))
                            .background(accent.copy(alpha = bg)).padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("发布动态", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
        if (posts.isEmpty()) {
            item(key = "empty") { EmptyState("还没有动态", "发布第一条动态，AI 好友会来点赞和评论") }
        }
        items(posts, key = { it.key }) { kp ->
            val animate = remember(kp.key) {
                val fresh = kp.key !in doneKeys
                doneKeys.add(kp.key)
                fresh
            }
            PostCard(kp.post, accent, myName, animate, { onLike(kp.post) }, { onOpenComments(kp.post) }, { onDelete(kp.post) })
        }
    }
}

@Composable
private fun PostCard(
    post: JSONObject, accent: Color, myName: String, animate: Boolean,
    onLike: () -> Unit, onOpenComments: () -> Unit, onDelete: () -> Unit
) {
    val isAi = post.optBoolean("ai")
    val likedBy = post.optJSONArray("likedBy")
    var liked = false
    if (likedBy != null) for (i in 0 until likedBy.length()) if (likedBy.optString(i) == "me") liked = true
    val comments = post.optJSONArray("comments") ?: JSONArray()
    var menuOpen by remember(post.optString("id")) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().bounceIn(enabled = animate).glassSurface(RoundedCornerShape(24.dp), 1.2f, 3.dp).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.optString("avatar"), post.optString("author"), 42)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.optString("author").ifBlank { myName }, fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isAi) {
                        Spacer(Modifier.width(6.dp))
                        Text("AI", fontSize = 9.sp, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text(relativeTime(post.optLong("time", 0L)).ifBlank { if (isAi) "AI 好友" else "" }, fontSize = 11.sp, color = Muted)
            }
            if (!isAi) {
                Box {
                    Icon(Icons.Default.MoreHoriz, "更多", tint = Muted, modifier = Modifier.size(26.dp).bounceClick { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("删除动态", color = Color(0xFFD64255)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFD64255)) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
        }
        if (post.optString("content").isNotBlank()) {
            Text(post.optString("content"), modifier = Modifier.padding(top = 12.dp), fontSize = 15.sp, lineHeight = 22.sp, color = Ink)
        }
        if (post.optString("image").isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            PickedPhoto(post.optString("image"), Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp), maxPx = 1000)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LikeButton(liked, post.optInt("likes"), accent, onLike)
            Spacer(Modifier.width(18.dp))
            Row(
                Modifier.bounceClick { onOpenComments() }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(5.dp))
                Text("${comments.length()}", color = Muted, fontSize = 14.sp)
            }
        }
        if (comments.length() > 0) {
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.04f)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val from = maxOf(0, comments.length() - 3)
                for (i in from until comments.length()) {
                    val c = comments.optJSONObject(i) ?: continue
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(c.optString("author")) }
                            append("：" + c.optString("content"))
                        },
                        fontSize = 13.sp, lineHeight = 18.sp, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                }
                if (comments.length() > 3) {
                    Text("查看全部 ${comments.length()} 条评论", fontSize = 12.sp, color = Muted, modifier = Modifier.bounceClick { onOpenComments() })
                }
            }
        }
    }
}

@Composable
private fun LikeButton(liked: Boolean, count: Int, accent: Color, onClick: () -> Unit) {
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    var previous by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !previous) {
            pop.snapTo(0.55f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        previous = liked
    }
    Row(Modifier.bounceClick(pressedScale = 0.85f) { onClick() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (liked) "取消点赞" else "点赞",
            tint = if (liked) Color(0xFFFF4D6A) else Muted,
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
        )
        Spacer(Modifier.width(5.dp))
        Text("$count", color = if (liked) Color(0xFFFF4D6A) else Muted, fontSize = 14.sp)
    }
}

// ───────────────────────────── Add AI tab ─────────────────────────────

@Composable
fun AddAiTab(
    data: JSONObject, revision: Int, accent: Color,
    onImport: () -> Unit, onCreate: () -> Unit, onGroup: () -> Unit, onOpenChat: (String) -> Unit
) {
    val arr = data.optJSONArray("personas") ?: JSONArray()
    val list = remember(revision, arr.length()) { (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Text("导入已有角色，或亲手创建一个新的 AI 好友", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
        item(key = "big-cards") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigActionCard(Modifier.weight(1f), "导入 AI", "从 ZIP 恢复角色、记录和 API 配置", Icons.Default.FolderOpen, Color(0xFF635BFF), 0, 2300, onImport)
                BigActionCard(Modifier.weight(1f), "创建 AI", "自定义名字、人设、服务商", Icons.Default.AutoAwesome, Color(0xFF13A98B), 90, 2700, onCreate)
            }
        }
        item(key = "group-card") {
            Row(
                Modifier.fillMaxWidth().bounceIn(delayMillis = 160).bounceClick(pressedScale = 0.96f) { onGroup() }
                    .glassSurface(RoundedCornerShape(26.dp), 1.3f, 4.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Groups, null, tint = accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("创建群聊", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                    Text("选择已创建的 AI 一起聊天", fontSize = 12.sp, color = Muted)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        item(key = "mine-title") {
            Text("我的 AI 角色 · ${list.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
        }
        if (list.isEmpty()) {
            item(key = "mine-empty") { EmptyState("还没有 AI 角色", "点上面的卡片创建或导入") }
        } else {
            itemsIndexed(list, key = { i, p -> p.optString("id").ifBlank { "p$i" } }) { _, p ->
                Row(
                    Modifier.fillMaxWidth().bounceClick(pressedScale = 0.97f) { onOpenChat(p.optString("id")) }
                        .glassSurface(RoundedCornerShape(22.dp), 1.2f).padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(p.optString("avatar"), p.optString("name"), 46)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.optString("name"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.optString("bio").ifBlank { p.optString("persona") }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Muted)
                    }
                    Text(p.optString("provider", "DeepSeek"), fontSize = 10.sp, color = accent, modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun BigActionCard(
    modifier: Modifier, title: String, subtitle: String, icon: ImageVector, color: Color,
    delay: Int, floatMillis: Int, onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "float-$title")
    val bob = transition.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(floatMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    Column(
        modifier.height(176.dp)
            .graphicsLayer { translationY = bob.value * density }
            .bounceIn(delayMillis = delay, initialScale = 0.7f, rise = 28f)
            .bounceClick(pressedScale = 0.92f) { onClick() }
            .glassSurface(RoundedCornerShape(28.dp), 1.35f, 6.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(subtitle, fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

// ───────────────────────────── Settings building blocks ─────────────────────────────

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(title, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        Column(
            Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp), 1.25f).padding(horizontal = 16.dp, vertical = 8.dp),
            content = content
        )
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = Muted, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingSlider(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0,
    onFinished: () -> Unit = {}, onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinished, valueRange = range, steps = steps)
    }
}

@Composable
fun SettingChips(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { opt -> FilterChip(selected = selected == opt, onClick = { onSelect(opt) }, label = { Text(opt) }) }
        }
    }
}

@Composable
fun SettingButton(label: String, icon: ImageVector, accent: Color, filled: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).bounceClick(pressedScale = 0.96f) { onClick() }
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) accent else accent.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (filled) Color.White else accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (filled) Color.White else accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
