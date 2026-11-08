package com.agent.arena

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 프롬프트에 넣을 기기 정보 / 설치 앱 / 현재 화면 덤프. */
object DeviceState {

    fun info(ctx: Context): String {
        val dm = ctx.resources.displayMetrics
        val bat = try {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            if (level >= 0) "${level * 100 / scale}%${if (charging) " (charging)" else ""}" else "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        val tz = TimeZone.getDefault()
        val now = SimpleDateFormat("yyyy-MM-dd EEE HH:mm", Locale.getDefault()).apply { timeZone = tz }.format(Date())
        return buildString {
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("screen: ${dm.widthPixels}x${dm.heightPixels}px")
            appendLine("locale: ${Locale.getDefault().toLanguageTag()}, timezone: ${tz.id}")
            appendLine("time: $now")
            append("battery: $bat")
        }
    }

    /** 앱 라벨만 쉼표로 나열(패키지는 launch_app 이 기기에서 직접 해석). 토큰 절약. */
    fun apps(ctx: Context, max: Int = 160): String {
        val list = DeviceActions.listApps(ctx)
        val labels = list.take(max).joinToString(", ") { it.first }
        return if (list.size > max) "$labels, … (+${list.size - max} more; use list_apps)" else labels
    }

    suspend fun screen(): String = withContext(Dispatchers.Default) {
        AgentAccessibilityService.instance?.dumpScreen()
            ?: "(접근성 서비스가 꺼져 있어 화면을 읽을 수 없음. 시스템 API 명령(volume/intent 등)만 사용 가능. 화면 조작이 필요하면 need_user 로 접근성 서비스 활성화를 요청)"
    }
}
