package com.agent.arena

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.agent.arena.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val vm: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱은 순수 검정 다크 테마 고정 → 시스템 바 아이콘은 항상 밝게
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val ctl = AgentApp.instance.controller
        ctl.start()
        setContent {
            AppRoot(
                vm = vm,
                ctl = ctl,
                onOpenAccessibility = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
        }
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush() // 로그인 쿠키 영속화
    }
}
