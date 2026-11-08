package com.agent.arena.ui

import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.agent.arena.AgentViewModel
import com.agent.arena.ArenaController
import com.agent.arena.CrashReport
import com.agent.arena.Phase

@Composable
fun AppRoot(vm: AgentViewModel, ctl: ArenaController, onOpenAccessibility: () -> Unit) {
    val phase by ctl.phase.collectAsState()
    val hint by ctl.webHint.collectAsState()
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var report by remember { mutableStateOf(CrashReport.read(appCtx)) }

    Box(Modifier.fillMaxSize().background(Pal.bg)) {
        // WebView 는 항상 트리에 붙어 있다(숨김 상태에서도 JS/쿠키 동작). CHAT 이면 대화 UI 가 위를 덮는다.
        Column(
            Modifier.fillMaxSize().then(
                if (phase == Phase.WEB) Modifier.systemBarsPadding().imePadding() else Modifier
            )
        ) {
            if (phase == Phase.WEB) WebBanner(hint) { ctl.closeWeb() }
            AndroidView(
                factory = {
                    ctl.webView.also { v -> (v.parent as? ViewGroup)?.removeView(v) }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        val r = report
        if (r != null) {
            CrashScreen(r) { CrashReport.clear(appCtx); report = null }
        } else {
            Crossfade(targetState = phase, animationSpec = tween(380), label = "phase") { ph ->
                when (ph) {
                    Phase.CHAT -> ChatScreen(vm, onOpenAccessibility)
                    Phase.LOADING -> LoadingScreen()
                    Phase.WEB -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun WebBanner(hint: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Pal.bg).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(hint, modifier = Modifier.weight(1f), style = TextStyle(color = Pal.text, fontSize = 14.sp))
        Spacer(Modifier.size(12.dp))
        Box(
            Modifier.bounceClick(onClick = onClose).clip(RoundedCornerShape(50)).background(Pal.surfaceHi)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) { BasicText("닫기", style = TextStyle(color = Pal.text, fontSize = 14.sp)) }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize().background(Pal.bg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spinner(Pal.accent, Modifier.size(40.dp))
        Spacer(Modifier.height(22.dp))
        BasicText("LMArena에 연결하는 중…", style = TextStyle(color = Pal.text, fontSize = 16.sp))
        Spacer(Modifier.height(6.dp))
        BasicText("Google 로그인 화면으로 이동합니다", style = TextStyle(color = Pal.sub, fontSize = 13.sp))
    }
}

@Composable
private fun CrashScreen(report: String, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Pal.bg).systemBarsPadding().padding(16.dp)) {
        BasicText("이전 실행에서 오류로 종료되었습니다", style = TextStyle(color = Pal.error, fontSize = 17.sp))
        Spacer(Modifier.height(6.dp))
        BasicText("아래 내용을 길게 눌러 복사해 전달해 주세요.", style = TextStyle(color = Pal.sub, fontSize = 13.sp))
        Spacer(Modifier.height(12.dp))
        SelectionContainer(Modifier.weight(1f)) {
            BasicText(report, modifier = Modifier.verticalScroll(rememberScrollState()), style = TextStyle(color = Pal.text, fontSize = 11.sp, lineHeight = 15.sp))
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().bounceClick(onClick = onContinue).clip(RoundedCornerShape(50)).background(Pal.accent).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText("계속하기", style = TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp)) }
    }
}
