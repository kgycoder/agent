package com.agent.arena

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/** 화면 읽기(UI 트리 덤프) + 탭/스와이프/입력/전역 키/앱 실행 등 기기 조작 실행기. */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        @Volatile var instance: AgentAccessibilityService? = null
            private set

        private const val MAX_NODES = 160
    }

    private val nodes = HashMap<Int, AccessibilityNodeInfo>()

    // Agent Action Cursor (접근성 오버레이)
    private var cursorInst: AgentCursorOverlay? = null
    private val cursor: AgentCursorOverlay
        get() = cursorInst ?: AgentCursorOverlay(this).also { cursorInst = it }

    fun cursorShow(label: String) { cursor.show(); cursor.setLabel(label, CursorState.WORKING) }
    fun cursorLabel(label: String, state: CursorState = CursorState.WORKING) { cursorInst?.setLabel(label, state) }
    fun cursorHide(delayMs: Long = 0) { cursorInst?.hide(delayMs) }

    private suspend fun cursorMoveTo(x: Float, y: Float) {
        cursor.show()
        cursor.moveTo(x, y)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cursorInst?.destroy()
        cursorInst = null
        instance = null
        _connected.value = false
        return super.onUnbind(intent)
    }

    @Volatile private var lastEventAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lastEventAt = SystemClock.uptimeMillis()
    }

    /** 고정 delay 대신: 화면 이벤트가 quietMs 동안 잠잠해질 때까지만 기다린다(최대 maxMs). */
    private suspend fun settle(quietMs: Long = 140, maxMs: Long = 1400) {
        val start = SystemClock.uptimeMillis()
        delay(50)
        while (true) {
            val now = SystemClock.uptimeMillis()
            if (now - lastEventAt >= quietMs || now - start >= maxMs) return
            delay(25)
        }
    }
    override fun onInterrupt() {}

    // ------------------------------------------------------------ screen dump

    fun dumpScreen(): String {
        val root = rootInActiveWindow
            ?: return "(화면 정보를 읽을 수 없음: 잠금/보안 화면이거나 전환 중일 수 있음. wait 후 다시 확인)"
        val pkg = root.packageName?.toString().orEmpty()
        nodes.clear()
        if (pkg == packageName) {
            return "foreground_package: $pkg\n(현재 앞에 있는 화면은 에이전트 앱 자신입니다. 기기 조작이 필요하면 launch_app 또는 key home 으로 이동하세요.)"
        }
        val sb = StringBuilder()
        sb.appendLine("foreground_package: $pkg")
        var id = 0

        fun walk(n: AccessibilityNodeInfo) {
            if (!n.isVisibleToUser) return
            val text = n.text?.toString()?.replace("\n", " ")?.take(100)
            val desc = n.contentDescription?.toString()?.replace("\n", " ")?.take(100)
            val interesting = !text.isNullOrBlank() || !desc.isNullOrBlank() ||
                n.isClickable || n.isEditable || n.isScrollable || n.isCheckable
            if (interesting && id < MAX_NODES) {
                id++
                nodes[id] = n
                val r = Rect()
                n.getBoundsInScreen(r)
                val flags = ArrayList<String>()
                if (n.isClickable) flags.add("click")
                if (n.isLongClickable) flags.add("long")
                if (n.isEditable) flags.add("edit")
                if (n.isScrollable) flags.add("scroll")
                if (n.isCheckable) flags.add(if (n.isChecked) "checked" else "unchecked")
                if (n.isFocused) flags.add("focused")
                if (!n.isEnabled) flags.add("disabled")
                sb.append("[$id] ${n.className?.toString()?.substringAfterLast('.')}")
                if (!text.isNullOrBlank()) sb.append(" text=\"$text\"")
                if (!desc.isNullOrBlank()) sb.append(" desc=\"$desc\"")
                sb.append(" center=(${r.centerX()},${r.centerY()})")
                if (flags.isNotEmpty()) sb.append(" ${flags.joinToString(",")}")
                sb.appendLine()
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(root)
        if (id == 0) sb.appendLine("(상호작용 가능한 요소가 보이지 않음)")
        if (id >= MAX_NODES) sb.appendLine("(요소가 많아 일부만 표시됨. scroll 로 더 확인)")
        return sb.toString().trimEnd()
    }

    // ------------------------------------------------------------ command execution

    /** 명령 1개를 실행하고 사람이 읽을 수 있는 결과 문자열("ok: ..." / "error: ...")을 반환. */
    suspend fun execute(c: JSONObject): String {
        val a = c.optString("action").lowercase().trim()
        return try {
            when (a) {
                // ---- 화면 조작
                "tap", "click" -> tap(c, longPress = false)
                "long_press", "longpress" -> tap(c, longPress = true)
                "swipe" -> swipe(c)
                "scroll" -> scroll(c.optString("direction", "down"))
                "type", "input", "set_text" -> typeText(c)
                "key" -> key(c.optString("key"))
                "back", "home", "recents", "notifications", "quick_settings", "lock_screen" -> key(a)
                "wait" -> {
                    val ms = c.optLong("ms", 1000).coerceIn(100, 10_000)
                    delay(ms); "ok: waited ${ms}ms"
                }
                "wait_for" -> waitFor(c)
                "read_screen" -> "ok: screen will be attached"
                // ---- 앱/URL
                "launch_app", "open_app" -> launchApp(c)
                "open_url" -> openUrl(c.optString("url"))
                // ---- 시스템 API 직접 명령 (UI 조작 불필요)
                "intent" -> DeviceActions.intent(this, c).also { settle() }
                "search", "web_search" -> DeviceActions.webSearch(this, c.optString("query")).also { settle() }
                "open_settings", "settings" -> DeviceActions.openSettings(this, c.optString("page")).also { settle() }
                "volume" -> DeviceActions.volume(this, c)
                "brightness" -> DeviceActions.brightness(this, c)
                "torch", "flashlight" -> DeviceActions.torch(this, c.optBoolean("on", true))
                "media" -> DeviceActions.media(this, c.optString("key"))
                "set_alarm" -> DeviceActions.setAlarm(this, c)
                "set_timer" -> DeviceActions.setTimer(this, c)
                "dial" -> DeviceActions.dial(this, c.optString("number")).also { settle() }
                "sms", "compose_sms" -> DeviceActions.composeSms(this, c).also { settle() }
                "clipboard_set" -> DeviceActions.clipboardSet(this, c.optString("text"))
                "list_apps" -> "ok:\n" + DeviceActions.appsText(this)
                "info" -> "ok:\n" + DeviceState.info(this)
                else -> "error: unknown action '$a'"
            }
        } catch (e: Exception) {
            "error: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    // ------------------------------------------------------------ 라이브 노드 검색 (화면이 바뀌어도 한 배치 안에서 연쇄 가능)

    private fun matchNodes(root: AccessibilityNodeInfo, q: String, exact: Boolean): List<AccessibilityNodeInfo> {
        val found = root.findAccessibilityNodeInfosByText(q) ?: return emptyList()
        val vis = found.filter { it.isVisibleToUser }
        if (!exact) return vis
        val lq = q.trim().lowercase()
        return vis.filter {
            it.text?.toString()?.trim()?.lowercase() == lq ||
                it.contentDescription?.toString()?.trim()?.lowercase() == lq
        }
    }

    private suspend fun findLive(q: String, timeoutMs: Long): AccessibilityNodeInfo? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        do {
            val root = rootInActiveWindow
            if (root != null) {
                val exact = matchNodes(root, q, true)
                val hit = exact.firstOrNull() ?: matchNodes(root, q, false).firstOrNull()
                if (hit != null) return hit
            }
            delay(120)
        } while (SystemClock.uptimeMillis() < end)
        return null
    }

    private suspend fun waitFor(c: JSONObject): String {
        val q = c.optString("text")
        if (q.isBlank()) return "error: wait_for requires text"
        val t = c.optLong("timeout_ms", 4000).coerceIn(300, 12_000)
        findLive(q, t) ?: return "error: '$q' 가 ${t}ms 안에 나타나지 않음"
        return "ok: found '$q'"
    }

    private fun firstEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null || !n.isVisibleToUser) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) firstEditable(n.getChild(i))?.let { return it }
        return null
    }

    private suspend fun tap(c: JSONObject, longPress: Boolean): String {
        val id = c.optInt("id", -1)
        val label = c.optString("text").ifBlank { c.optString("desc") }
        var node: AccessibilityNodeInfo? = null
        val x: Float
        val y: Float
        if (id > 0) {
            val nd = nodes[id] ?: return "error: id $id 는 최신 DEVICE_STATE 에 없음(만료). text 로 탭하거나 read_screen 하세요"
            node = nd
            val r = Rect(); nd.getBoundsInScreen(r)
            x = r.centerX().toFloat(); y = r.centerY().toFloat()
        } else if (label.isNotBlank()) {
            val nd = findLive(label, c.optLong("timeout_ms", 2500).coerceIn(200, 10_000))
                ?: return "error: 화면에서 '$label' 을(를) 찾지 못함"
            node = nd
            val r = Rect(); nd.getBoundsInScreen(r)
            x = r.centerX().toFloat(); y = r.centerY().toFloat()
        } else {
            if (!c.has("x") || !c.has("y")) return "error: tap requires id, text or x,y"
            x = c.getDouble("x").toFloat(); y = c.getDouble("y").toFloat()
        }
        cursorMoveTo(x, y)
        cursor.press()
        if (node != null && !longPress) {
            var n: AccessibilityNodeInfo? = node
            while (n != null && !n.isClickable) n = n.parent
            if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                settle(); return "ok: clicked ${if (id > 0) "id $id" else "'$label'"}"
            }
        }
        val ok = gesture(point(x, y), if (longPress) 700 else 50)
        settle()
        val what = if (longPress) "long-pressed" else "tapped"
        return if (ok) "ok: $what (${x.toInt()},${y.toInt()})" else "error: gesture failed"
    }

    private suspend fun swipe(c: JSONObject): String {
        val x1 = c.getDouble("x1").toFloat(); val y1 = c.getDouble("y1").toFloat()
        val x2 = c.getDouble("x2").toFloat(); val y2 = c.getDouble("y2").toFloat()
        return swipeInternal(x1, y1, x2, y2, c.optLong("duration_ms", 350).coerceIn(50, 3000), "swiped")
    }

    private suspend fun swipeInternal(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long, label: String): String {
        cursorMoveTo(x1, y1)
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        cursor.glideTo(x2, y2) // 제스처가 진행되는 동안 커서도 같이 이동
        val ok = gesture(p, dur)
        settle(quietMs = 120, maxMs = 900)
        return if (ok) "ok: $label" else "error: gesture failed"
    }

    /** direction = 콘텐츠를 이동하고 싶은 방향 (down: 페이지 아래쪽으로 = 손가락은 위로). */
    private suspend fun scroll(direction: String): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        return when (direction.lowercase()) {
            "down" -> swipeInternal(w / 2, h * 0.72f, w / 2, h * 0.28f, 260, "scrolled down")
            "up" -> swipeInternal(w / 2, h * 0.28f, w / 2, h * 0.72f, 260, "scrolled up")
            "right" -> swipeInternal(w * 0.8f, h / 2, w * 0.2f, h / 2, 260, "scrolled right")
            "left" -> swipeInternal(w * 0.2f, h / 2, w * 0.8f, h / 2, 260, "scrolled left")
            else -> "error: direction must be up/down/left/right"
        }
    }

    private suspend fun typeText(c: JSONObject): String {
        val text = c.optString("text")
        val id = c.optInt("id", -1)
        val node: AccessibilityNodeInfo? = when {
            id > 0 -> nodes[id]
            else -> rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: firstEditable(rootInActiveWindow)
                ?: nodes.values.firstOrNull { it.isEditable }
        }
        if (node == null) return "error: 입력 가능한 필드를 찾지 못함 (먼저 필드를 tap 하세요)"
        val r = Rect(); node.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) {
            cursorMoveTo(r.centerX().toFloat(), r.centerY().toFloat())
            cursor.press()
        }
        if (!node.isFocused) node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val base = if (c.optBoolean("append", false) && !node.isShowingHintText) node.text?.toString().orEmpty() else ""
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, base + text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) return "error: ACTION_SET_TEXT 실패"
        if (c.optBoolean("submit", false) && Build.VERSION.SDK_INT >= 30) {
            delay(120)
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        settle(quietMs = 100, maxMs = 800)
        return "ok: typed ${text.length} chars"
    }

    private suspend fun key(k: String): String {
        val action = when (k.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock_screen" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return "error: lock_screen requires Android 9+"
            else -> return "error: unknown key '$k'"
        }
        val ok = performGlobalAction(action)
        settle(quietMs = 160, maxMs = 1000)
        return if (ok) "ok: key $k" else "error: key $k failed"
    }

    private suspend fun launchApp(c: JSONObject): String {
        val (pkg, err) = DeviceActions.resolvePackage(this, c)
        if (pkg == null) return err ?: "error: launch_app failed"
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return "error: $pkg 실행 불가(미설치/실행 인텐트 없음)"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent)
        // 대상 앱이 전면에 올라올 때까지만 대기 (최대 3초), 그 뒤 화면 안정화
        val end = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < end) {
            if (rootInActiveWindow?.packageName?.toString() == pkg) break
            delay(80)
        }
        settle(quietMs = 220, maxMs = 1500)
        return "ok: launched $pkg"
    }

    private suspend fun openUrl(url: String): String {
        if (url.isBlank()) return "error: url is empty"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        settle(quietMs = 250, maxMs = 1800)
        return "ok: opened $url"
    }

    // ------------------------------------------------------------ gestures

    private fun point(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    private suspend fun gesture(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1)))
                .build()
            val dispatched = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!dispatched && cont.isActive) cont.resume(false)
        }
}
