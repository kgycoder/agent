package com.agent.arena.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.arena.AgentAccessibilityService
import com.agent.arena.AgentApp
import com.agent.arena.AgentViewModel
import com.agent.arena.ChatMessage
import com.agent.arena.Role
import kotlinx.coroutines.launch

// ====================================================================== 인사말

private object Greetings {
    private val general = listOf(
        "무엇을 도와드릴까요?",
        "오늘은 어떤 일을 처리할까요?",
        "폰은 제가 만져 볼게요.",
        "말만 하세요, 바로 해볼게요.",
        "지금 필요한 게 뭐예요?",
        "어떤 작업부터 시작할까요?",
        "귀찮은 일은 저한테 맡겨요.",
        "하고 싶은 일을 알려 주세요.",
        "무엇이든 부탁하세요.",
        "설정부터 검색까지, 한 번에.",
        "다음 할 일은 뭔가요?",
        "손이 필요하신가요?",
    )
    private var last = ""

    fun pick(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timed = when {
            h < 5 -> "늦은 시간이네요. 무엇을 도와드릴까요?"
            h < 11 -> "좋은 아침이에요. 오늘은 뭘 할까요?"
            h < 14 -> "점심 시간이네요. 필요한 일이 있나요?"
            h < 18 -> "좋은 오후예요. 무엇을 도와드릴까요?"
            else -> "편안한 저녁이에요. 무엇을 할까요?"
        }
        val pool = general + timed + timed // 시간대 인사말은 가중치 2
        var g = pool.random()
        var tries = 0
        while (g == last && tries++ < 8) g = pool.random()
        last = g
        return g
    }
}

private const val SUBTITLE = "기기를 직접 조작하는 AI 에이전트"

/** 인사말 등장/퇴장: 페이드 + 블러 + 살짝 떠오르는 이동. AnimatedVisibility 의 transition 에 붙어 함께 움직인다. */
@Composable
private fun AnimatedVisibilityScope.BlurFade(
    delayMs: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val aVal by transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) tween(700, delayMs, SoftEase) else tween(260) },
        label = "gAlpha",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val blur by transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) tween(800, delayMs, SoftEase) else tween(260) },
        label = "gBlur",
    ) { if (it == EnterExitState.Visible) 0f else 16f }
    val rise by transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) tween(800, delayMs, SoftEase) else tween(280) },
        label = "gRise",
    ) {
        when (it) {
            EnterExitState.PreEnter -> 18f
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> -12f
        }
    }
    Box(
        modifier
            .softBlur { blur * density }
            .graphicsLayer { alpha = aVal; translationY = rise * density },
    ) { content() }
}

@Composable
private fun GreetingArea(visible: Boolean, greeting: String, a11yOn: Boolean, onOpenAccessibility: () -> Unit) {
    val vs = remember { MutableTransitionState(false) }
    vs.targetState = visible
    AnimatedVisibility(
        visibleState = vs,
        enter = fadeIn(tween(1)),
        exit = fadeOut(tween(300)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BlurFade(delayMs = 0) { Logo() }
            Spacer(Modifier.height(18.dp))
            BlurFade(delayMs = 110) {
                BasicText(
                    greeting,
                    style = TextStyle(color = Pal.text, fontSize = 27.sp, lineHeight = 36.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
            }
            Spacer(Modifier.height(8.dp))
            BlurFade(delayMs = 220) {
                BasicText(SUBTITLE, style = TextStyle(color = Pal.sub, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
            if (!a11yOn) {
                Spacer(Modifier.height(26.dp))
                BlurFade(delayMs = 340) {
                    Box(
                        Modifier
                            .bounceClick(onClick = onOpenAccessibility)
                            .clip(RoundedCornerShape(50))
                            .background(Pal.surfaceHi)
                            .border(1.dp, Pal.accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                    ) { BasicText("접근성 서비스 켜기", style = TextStyle(color = Pal.accent, fontSize = 14.sp)) }
                }
            }
        }
    }
}

@Composable
private fun Logo() {
    val t = rememberInfiniteTransition(label = "logo")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "rot")
    val breathe by t.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breathe")
    SparkIcon(
        Pal.accent,
        Modifier.size(40.dp).graphicsLayer { rotationZ = rot; scaleX = breathe; scaleY = breathe },
    )
}

// ====================================================================== 화면

@Composable
fun ChatScreen(vm: AgentViewModel, onOpenAccessibility: () -> Unit) {
    val ctl = AgentApp.instance.controller
    val listState = rememberLazyListState()
    val a11y by AgentAccessibilityService.connected.collectAsState()
    val seen = remember { HashSet<Long>() }
    val greeting = remember(vm.chatEpoch) { Greetings.pick() }

    // 사용자가 위로 올려 읽는 중이면 자동 스크롤을 멈추고, 바닥으로 돌아오면 다시 따라간다.
    var stick by remember { mutableStateOf(true) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) stick = !listState.canScrollForward
                return Offset.Zero
            }
        }
    }
    val scope = rememberCoroutineScope()
    val onGrow: () -> Unit = {
        if (stick) scope.launch { listState.scrollBy(1_000_000f) }
    }

    val lastUserIndex = vm.messages.indexOfLast { it.role == Role.USER }
    LaunchedEffect(lastUserIndex, vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            stick = true
            listState.animateScrollToItem(vm.messages.lastIndex)
            listState.scrollBy(1_000_000f)
        }
    }

    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val dividerAlpha by animateFloatAsState(if (scrolled) 1f else 0f, tween(250), label = "divider")

    Column(
        Modifier.fillMaxSize().background(Pal.bg).windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TopBar(
            a11yOn = a11y,
            onSettings = onOpenAccessibility,
            onTitle = { ctl.toggleWeb() },
            onNew = { vm.newChat() },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = dividerAlpha }.background(Pal.border))

        // 메시지/인사말 영역: 입력창은 이 영역 "아래"에 따로 배치되므로 인사말이 절대 가려지지 않는다.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val roomy = maxHeight > 230.dp // 키보드가 올라와 공간이 좁으면 인사말은 부드럽게 접힌다
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GreetingArea(
                    visible = vm.messages.isEmpty() && roomy,
                    greeting = greeting,
                    a11yOn = a11y,
                    onOpenAccessibility = onOpenAccessibility,
                )
            }

            if (vm.messages.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(nested),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(vm.messages, key = { it.id }) { m ->
                        val fresh = remember { seen.add(m.id) }
                        AppearIn(enabled = fresh, modifier = Modifier.animateItem()) {
                            MessageItem(m, onGrow = onGrow)
                        }
                    }
                }
            }
        }

        InputBar(
            running = vm.running,
            draft = vm.draft,
            onDraft = { vm.draft = it },
            onSend = { vm.send(it); vm.draft = "" },
            onStop = { vm.stop() },
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        )
    }
}

// ====================================================================== 상단 바

@Composable
private fun TopBar(a11yOn: Boolean, onSettings: () -> Unit, onTitle: () -> Unit, onNew: () -> Unit) {
    val dot by animateColorAsState(if (a11yOn) Pal.ok else Pal.error, tween(400), label = "dot")
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).bounceClick(onClick = onSettings), contentAlignment = Alignment.Center) {
            SlidersIcon(Pal.text, Modifier.size(21.dp))
        }
        Row(
            Modifier.weight(1f).bounceClick(pressedScale = 0.96f, onClick = onTitle),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            BasicText("Arena Agent", style = TextStyle(color = Pal.text, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
        }
        Box(Modifier.size(44.dp).bounceClick(onClick = onNew), contentAlignment = Alignment.Center) {
            PlusIcon(Pal.text, Modifier.size(22.dp))
        }
    }
}

// ====================================================================== 입력창

@Composable
private fun InputBar(
    running: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lines by remember { mutableIntStateOf(1) }
    val canSend = draft.isNotBlank()
    val active = running || canSend

    val radius by animateDpAsState(if (lines > 1) 24.dp else 28.dp, tween(300, easing = SoftEase), label = "radius")
    val focusSource = remember { MutableInteractionSource() }
    val focused by focusSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(if (focused) Pal.accent.copy(alpha = 0.6f) else Pal.border, tween(280), label = "border")
    val shape = RoundedCornerShape(radius)

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            // 줄이 늘어나면 컨테이너가 스프링으로 부드럽게 팽창/수축
            .animateContentSize(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
            .background(Pal.surface, shape)
            .border(1.dp, borderColor, shape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
            val phAlpha by animateFloatAsState(if (draft.isEmpty()) 1f else 0f, tween(180), label = "ph")
            BasicText(
                "Arena Agent에게 메시지 보내기",
                style = TextStyle(color = Pal.faint, fontSize = 16.sp),
                modifier = Modifier.graphicsLayer { alpha = phAlpha },
            )
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                textStyle = TextStyle(color = Pal.text, fontSize = 16.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(Pal.accent),
                maxLines = 8,
                interactionSource = focusSource,
                onTextLayout = { lines = it.lineCount },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SendStopButton(running = running, canSend = canSend, active = active) {
            if (running) onStop() else onSend(draft)
        }
    }
}

@Composable
private fun SendStopButton(running: Boolean, canSend: Boolean, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        when {
            running -> Pal.text
            canSend -> Pal.accent
            else -> Pal.surfaceHi
        },
        tween(260), label = "btnBg",
    )
    val fg by animateColorAsState(
        when {
            running -> Pal.bg
            canSend -> Color.White
            else -> Pal.faint
        },
        tween(260), label = "btnFg",
    )
    val pop by animateFloatAsState(
        if (active) 1f else 0.9f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "btnPop",
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .size(40.dp)
            .bounceClick(enabled = active, pressedScale = 0.86f, onClick = onClick)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = running,
            transitionSpec = {
                (fadeIn(tween(220, 70)) + scaleIn(tween(300, 40, SoftEase), initialScale = 0.4f)) togetherWith
                    (fadeOut(tween(110)) + scaleOut(tween(160), targetScale = 0.4f))
            },
            label = "sendStop",
        ) { r ->
            if (r) StopIcon(fg, Modifier.size(18.dp)) else ArrowUpIcon(fg, Modifier.size(19.dp))
        }
    }
}

// ====================================================================== 메시지

/** 새로 생긴 항목: 페이드 + 블러 해제 + 살짝 올라오며 등장. */
@Composable
private fun AppearIn(enabled: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, tween(520, easing = SoftEase)) }
    val density = LocalDensity.current.density
    Box(
        modifier
            .softBlur { (1f - p.value) * 9f * density }
            .graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 16f * density },
    ) { content() }
}

@Composable
private fun MessageItem(m: ChatMessage, onGrow: () -> Unit) {
    if (m.role == Role.USER) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(22.dp)).background(Pal.userBubble)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                BasicText(m.text, style = TextStyle(color = Pal.text, fontSize = 16.sp, lineHeight = 23.sp))
            }
        }
    } else {
        AgentMessage(m, onGrow)
    }
}

@Composable
private fun AgentMessage(m: ChatMessage, onGrow: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // 처음 구성될 때 스트리밍 중이던 메시지만 풀어내는 애니메이션을 적용(스크롤로 다시 나타나면 즉시 표시)
    val animate = remember { m.streaming }
    val color = if (m.isError) Pal.error else Pal.text
    val copyText = m.text

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
            .pointerInput(copyText) {
                detectTapGestures(onLongPress = {
                    if (copyText.isNotBlank()) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("answer", copyText))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(ctx, "복사했어요", Toast.LENGTH_SHORT).show()
                    }
                })
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (m.steps.isNotEmpty()) StepsView(m.steps, active = m.streaming)
        if (m.planning) ThinkingRow("작업 계획 중…")
        if (m.text.isNotBlank()) {
            StreamingMarkdown(text = m.text, animate = animate, color = color, onGrow = onGrow)
        } else if (m.streaming && !m.planning) {
            ThinkingRow(if (m.steps.isEmpty()) "생각 중…" else "다음 단계 확인 중…")
        }
    }
}

@Composable
private fun ThinkingRow(label: String) {
    val t = rememberInfiniteTransition(label = "think")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "r")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically) {
        SparkIcon(Pal.accent, Modifier.size(16.dp).graphicsLayer { rotationZ = rot })
        Spacer(Modifier.width(10.dp))
        BasicText(label, style = TextStyle(color = Pal.sub, fontSize = 14.sp), modifier = Modifier.graphicsLayer { alpha = a })
    }
}

@Composable
private fun StepsView(steps: List<String>, active: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val collapsible = !active && steps.size > 2
    val show = !collapsible || expanded
    val chev by animateFloatAsState(if (expanded) 180f else 0f, tween(300, easing = SoftEase), label = "chev")

    Column(
        Modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (collapsible) {
            Row(
                Modifier.bounceClick(pressedScale = 0.97f) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("${steps.size}단계 수행함", style = TextStyle(color = Pal.sub, fontSize = 14.sp))
                Spacer(Modifier.width(4.dp))
                ChevronIcon(Pal.sub, Modifier.size(16.dp).graphicsLayer { rotationZ = chev })
            }
        }
        if (show) {
            steps.forEachIndexed { i, s ->
                val vs = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = vs,
                    enter = fadeIn(tween(350)) + expandVertically(tween(350, easing = SoftEase)) +
                        slideInVertically(tween(400, easing = SoftEase)) { it / 3 },
                ) {
                    StepLine(s, running = active && i == steps.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun StepLine(text: String, running: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (running) Spinner(Pal.accent, Modifier.size(14.dp)) else CheckIcon(Pal.sub, Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        BasicText(text, style = TextStyle(color = if (running) Pal.text else Pal.sub, fontSize = 14.sp, lineHeight = 19.sp), modifier = Modifier.weight(1f))
    }
}

@Composable
fun Spinner(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Canvas(modifier) {
        val sw = 2.dp.toPx()
        val sz = Size(size.width - sw, size.height - sw)
        drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, Offset(sw / 2, sw / 2), sz, style = Stroke(sw))
        drawArc(color, angle, 100f, false, Offset(sw / 2, sw / 2), sz, style = Stroke(sw, cap = StrokeCap.Round))
    }
}
