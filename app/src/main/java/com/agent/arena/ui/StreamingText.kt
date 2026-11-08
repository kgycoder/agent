package com.agent.arena.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest

private const val TAIL = 48          // 페이드 중인 최대 글자 수
private const val FADE_MS = 520f     // 글자 하나가 투명→선명(블러→선명)으로 바뀌는 시간

private class RevealState(initial: Int) {
    var vis by mutableIntStateOf(initial)
    var tick by mutableLongStateOf(0L)
    var lastReveal = 0L
    val times = ArrayDeque<Long>()
}

/**
 * 스트리밍 답변 렌더러.
 *  - 도착한 글자를 한 번에 뿌리지 않고, 쌓인 양에 비례한 속도로 부드럽게 풀어낸다.
 *  - 새로 나타난 글자는 "블러 + 투명" 에서 서서히 선명해진다(그림자 블러로 글리프 흐림을 표현).
 */
@Composable
fun StreamingMarkdown(
    text: String,
    animate: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onGrow: () -> Unit = {},
) {
    val st = remember { RevealState(if (animate) 0 else text.length) }
    val latest = rememberUpdatedState(text)

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        snapshotFlow { latest.value.length }.collectLatest { len ->
            if (st.vis > len) st.vis = len
            var prev = withFrameMillis { it }
            var acc = 0f
            while (true) {
                val now = withFrameMillis { it }
                val dt = ((now - prev) / 1000f).coerceIn(0f, 0.05f)
                prev = now
                if (st.vis < len) {
                    val backlog = len - st.vis
                    // 쌓인 만큼 빨라지는 속도(글자/초): 천천히 흐르다가 밀리면 따라잡는다
                    acc += (40f + backlog * 6f).coerceAtMost(1400f) * dt
                    val n = acc.toInt()
                    if (n > 0) {
                        acc -= n
                        val add = minOf(n, backlog)
                        repeat(add) {
                            st.times.addLast(now)
                            if (st.times.size > TAIL) st.times.removeFirst()
                        }
                        st.vis += add
                        st.lastReveal = now
                    }
                }
                st.tick = now
                if (st.vis >= len && now - st.lastReveal > FADE_MS + 80) break
            }
        }
    }

    val visCount = if (animate) minOf(st.vis, text.length) else text.length
    val visText = if (visCount >= text.length) text else text.substring(0, visCount)
    val blocks = remember(visText) { Markdown.parse(visText) }
    val now = st.tick // 읽어서 프레임마다 꼬리 글자 알파를 갱신
    val density = LocalDensity.current.density

    Column(
        modifier.onSizeChanged { onGrow() },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            val isLast = index == blocks.lastIndex
            when (block) {
                is MdCode -> CodeBlock(block)
                is MdText -> {
                    val shown = if (animate && isLast) fadeTail(block.text, st.times, now, color, density) else block.text
                    MdTextBlock(block, shown, color)
                }
            }
        }
    }
}

/** 마지막 블록의 끝 글자들에 (알파 + 블러 그림자) 스팬을 입힌다. */
private fun fadeTail(base: AnnotatedString, times: ArrayDeque<Long>, now: Long, color: Color, density: Float): AnnotatedString {
    val len = base.text.length
    if (len == 0 || times.isEmpty()) return base
    var b: AnnotatedString.Builder? = null
    val m = minOf(len, times.size)
    for (d in 0 until m) {
        val age = (now - times[times.size - 1 - d]) / FADE_MS
        if (age >= 1f) break // 더 앞쪽 글자는 이미 선명
        val e = (if (age < 0f) 0f else age)
        val a = 1f - (1f - e) * (1f - e) * (1f - e) // ease-out cubic
        if (b == null) b = AnnotatedString.Builder(base)
        val idx = len - 1 - d
        b.addStyle(
            SpanStyle(
                color = color.copy(alpha = a * a),
                shadow = Shadow(color = color.copy(alpha = (1f - a) * 0.85f), offset = Offset.Zero, blurRadius = (1f - a) * 9f * density),
            ),
            idx, idx + 1,
        )
    }
    return b?.toAnnotatedString() ?: base
}

@Composable
private fun MdTextBlock(block: MdText, shown: AnnotatedString, color: Color) {
    val body = TextStyle(color = color, fontSize = 16.sp, lineHeight = 25.sp)
    when (block.kind) {
        MdKind.H1 -> BasicText(shown, style = body.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 4.dp))
        MdKind.H2 -> BasicText(shown, style = body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 2.dp))
        MdKind.BULLET, MdKind.NUM -> Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(if (block.kind == MdKind.NUM) 26.dp else 20.dp)) {
                BasicText(block.marker, style = body.copy(color = Pal.sub))
            }
            BasicText(shown, style = body, modifier = Modifier.weight(1f))
        }
        MdKind.QUOTE -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Pal.accent.copy(alpha = 0.7f)))
            BasicText(shown, style = body.copy(color = Pal.sub), modifier = Modifier.padding(start = 12.dp))
        }
        MdKind.PARA -> BasicText(shown, style = body, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CodeBlock(b: MdCode) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.code),
    ) {
        if (b.lang.isNotBlank()) {
            BasicText(
                b.lang,
                style = TextStyle(color = Pal.sub, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp),
            )
        }
        BasicText(
            b.code,
            style = TextStyle(color = Pal.text, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
        )
    }
}
