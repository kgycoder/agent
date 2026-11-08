package com.agent.arena

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class Role { USER, AGENT }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String = "",
    val steps: List<String> = emptyList(),
    val streaming: Boolean = false,
    val planning: Boolean = false,
    val isError: Boolean = false,
)

class AgentViewModel : ViewModel() {

    companion object {
        private const val MAX_STEPS = 40
        private const val MAX_COMMANDS = 12

        /** 결과가 권위 있고 화면을 바꾸지 않는 시스템 직접 명령: 화면 덤프를 다시 보낼 필요가 없다. */
        private val DIRECT = setOf(
            "volume", "brightness", "torch", "flashlight", "media", "info", "list_apps",
            "clipboard_set", "set_alarm", "set_timer",
        )
    }

    private val app get() = AgentApp.instance
    private val ctl get() = AgentApp.instance.controller

    val messages = mutableStateListOf<ChatMessage>()
    var running by mutableStateOf(false)
        private set

    /** 입력창 초안(화면 전환/웹 보기 토글에도 유지). */
    var draft by mutableStateOf("")

    /** 새 채팅마다 증가 → 인사말을 새로 뽑는 키. */
    var chatEpoch by mutableStateOf(0)
        private set

    private var job: Job? = null
    private var sessionStarted = false
    private var nextId = 1L

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || running) return
        messages.add(ChatMessage(nextId++, Role.USER, t))
        val idx = messages.size
        messages.add(ChatMessage(nextId++, Role.AGENT, streaming = true))
        running = true
        job = viewModelScope.launch {
            try {
                runAgent(t, idx)
            } finally {
                running = false
                AgentAccessibilityService.instance?.cursorHide(1300)
            }
        }
    }

    fun stop() {
        if (!running) return
        job?.cancel()
        viewModelScope.launch { ctl.stopGeneration() }
    }

    /** 새 채팅 = LMArena URL 을 다시 열어 새 세션 시작. */
    fun newChat() {
        job?.cancel()
        running = false
        messages.clear()
        sessionStarted = false
        chatEpoch++
        viewModelScope.launch {
            ctl.stopGeneration()
            ctl.newChat()
        }
    }

    // ---------------------------------------------------------------- agent loop

    private fun upd(i: Int, f: (ChatMessage) -> ChatMessage) {
        if (i in 0 until messages.size) messages[i] = f(messages[i])
    }

    private fun finish(i: Int, text: String, error: Boolean) {
        upd(i) { it.copy(text = text, streaming = false, planning = false, isError = error) }
    }

    private fun isDirect(c: JSONObject) = c.optString("action").lowercase().trim() in DIRECT

    private suspend fun runAgent(userText: String, idx: Int) {
        var outgoing = if (!sessionStarted) AgentPrompt.first(userText, app) else AgentPrompt.followUp(userText)
        var step = 0
        var formatRetries = 0
        try {
            while (true) {
                step++
                if (step > MAX_STEPS) {
                    finish(idx, "최대 단계($MAX_STEPS)에 도달해 작업을 중단했습니다.", true); return
                }
                val ticket = ctl.send(outgoing)
                if (ticket == null) {
                    finish(idx, "LMArena로 메시지를 보내지 못했습니다. 상단 'Agent' 버튼으로 LMArena 페이지 상태(로그인/캡차)를 확인하세요.", true)
                    return
                }
                sessionStarted = true

                val raw = ctl.awaitResponse(ticket, earlyDone = { WorkParser.hasCompleteBlock(it) }) { partial ->
                    upd(idx) {
                        it.copy(
                            text = WorkParser.visibleStreaming(partial),
                            planning = WorkParser.isPlanning(partial),
                        )
                    }
                }
                if (raw.isBlank()) {
                    finish(idx, "LMArena 응답을 받지 못했습니다. (시간 초과 또는 사이트 오류)\n진단: ${ctl.debug(ticket)}", true); return
                }

                val parsed = WorkParser.parse(raw)
                val block = parsed.block
                if (block == null) {
                    if (parsed.looksBroken && formatRetries < 2) {
                        formatRetries++
                        outgoing = AgentPrompt.formatError()
                        continue
                    }
                    // work 블록이 없으면 일반 대화 답변으로 간주
                    finish(idx, parsed.visible.ifBlank { raw }, false)
                    return
                }
                formatRetries = 0

                val cmds = block.commands.take(MAX_COMMANDS)
                val hasUi = cmds.any { !isDirect(it) }
                val terminal = block.status == "done" || block.status == "failed" || block.status == "need_user"

                // 진행 중 단계 표시. 종료 상태면 본문(최종 답변)은 그대로 둔다.
                upd(idx) {
                    it.copy(
                        steps = if (cmds.isNotEmpty() || !terminal) it.steps + block.summary.ifBlank { block.status } else it.steps,
                        text = if (terminal) parsed.visible else "",
                        planning = false,
                    )
                }

                val svc = AgentAccessibilityService.instance
                if (hasUi) svc?.cursorShow(block.summary.ifBlank { "작업 중" })
                val (results, anyError) = if (cmds.isNotEmpty()) executeAll(cmds) else "[]" to false

                // done 이라도 UI 명령이 섞였거나 실행이 실패했다면 화면을 확인하도록 한 번 더 진행
                val finalStatus = when {
                    block.status == "done" && (hasUi || anyError) && cmds.isNotEmpty() -> "continue"
                    block.status == "failed" || block.status == "need_user" || block.status == "done" -> block.status
                    else -> "continue"
                }
                when (finalStatus) {
                    "done" -> {
                        if (hasUi) svc?.cursorLabel("작업 완료", CursorState.DONE)
                        finish(idx, parsed.visible.ifBlank { block.summary }, false); return
                    }
                    "failed" -> {
                        svc?.cursorLabel("작업 실패", CursorState.FAILED)
                        finish(idx, parsed.visible.ifBlank { block.summary }, true); return
                    }
                    "need_user" -> {
                        svc?.cursorLabel("확인이 필요해요", CursorState.ASK)
                        finish(idx, parsed.visible.ifBlank { block.summary }, false); return
                    }
                }
                if (terminal) upd(idx) { it.copy(text = "") } // done→continue 로 바뀐 경우 임시 본문 제거
                if (hasUi) svc?.cursorLabel("다음 작업 생각 중…", CursorState.THINKING)
                val needScreen = hasUi || cmds.isEmpty() || cmds.any { it.optString("action") == "read_screen" }
                outgoing = AgentPrompt.result(step, results, needScreen)
            }
        } catch (e: CancellationException) {
            AgentAccessibilityService.instance?.cursorLabel("중단됨", CursorState.STOPPED)
            upd(idx) { it.copy(streaming = false, planning = false, text = it.text.ifBlank { "(중단됨)" }) }
            throw e
        } catch (e: Exception) {
            finish(idx, "오류가 발생했습니다: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }

    /** 명령을 순서대로 실행(첫 오류에서 중단). (결과 JSON, 오류 여부) */
    private suspend fun executeAll(cmds: List<JSONObject>): Pair<String, Boolean> {
        val svc = AgentAccessibilityService.instance
        val arr = JSONArray()
        var err = false
        for (c in cmds) {
            val direct = isDirect(c)
            val a = c.optString("action").lowercase().trim()
            // 서비스가 꺼져 있어도 직접 명령 중 일부는 앱 컨텍스트에서 바로 실행 가능
            val r = svc?.execute(c) ?: if (direct) runDirectWithoutService(c) else "error: accessibility service is not enabled (설정에서 Arena Agent 접근성 서비스를 켜야 함)"
            arr.put(JSONObject().put("action", a).put("result", r))
            if (r.startsWith("error")) { err = true; break }
        }
        return arr.toString() to err
    }

    private fun runDirectWithoutService(c: JSONObject): String = try {
        when (c.optString("action").lowercase().trim()) {
            "volume" -> DeviceActions.volume(app, c)
            "brightness" -> DeviceActions.brightness(app, c)
            "torch", "flashlight" -> DeviceActions.torch(app, c.optBoolean("on", true))
            "media" -> DeviceActions.media(app, c.optString("key"))
            "set_alarm" -> DeviceActions.setAlarm(app, c)
            "set_timer" -> DeviceActions.setTimer(app, c)
            "clipboard_set" -> DeviceActions.clipboardSet(app, c.optString("text"))
            "list_apps" -> "ok:\n" + DeviceActions.appsText(app)
            "info" -> "ok:\n" + DeviceState.info(app)
            else -> "error: accessibility service is not enabled"
        }
    } catch (e: Exception) {
        "error: ${e.javaClass.simpleName}: ${e.message}"
    }
}
