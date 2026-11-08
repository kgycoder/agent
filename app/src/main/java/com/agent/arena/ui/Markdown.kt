package com.agent.arena.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

enum class MdKind { PARA, H1, H2, QUOTE, BULLET, NUM }

sealed class MdBlock
class MdText(val text: AnnotatedString, val kind: MdKind, val marker: String = "") : MdBlock()
class MdCode(val lang: String, val code: String) : MdBlock()

/** 스트리밍 도중의 미완성 마크다운도 깨지지 않게 다루는 가벼운 파서. */
object Markdown {

    private val BOLD = SpanStyle(fontWeight = FontWeight.SemiBold)
    private val CODE = SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF1C1C1F), color = Color(0xFFBFD6FF))

    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^[-*•]\\s+(.*)$")
    private val numbered = Regex("^(\\d{1,3})[.)]\\s+(.*)$")

    fun inline(s: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            if (s.startsWith("**", i)) {
                val j = s.indexOf("**", i + 2)
                if (j < 0) { // 아직 닫히지 않은 굵게: 끝까지 굵게 처리(스트리밍 중 별표 깜빡임 방지)
                    withStyle(BOLD) { append(s.substring(i + 2)) }
                    i = s.length
                } else {
                    withStyle(BOLD) { append(s.substring(i + 2, j)) }
                    i = j + 2
                }
            } else if (s[i] == '`') {
                val j = s.indexOf('`', i + 1)
                if (j > i + 1) {
                    withStyle(CODE) { append(s.substring(i + 1, j)) }
                    i = j + 1
                } else {
                    append(s[i]); i++
                }
            } else {
                append(s[i]); i++
            }
        }
    }

    fun parse(src: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val lines = src.split("\n")
        val para = StringBuilder()
        fun flush() {
            if (para.isNotEmpty()) {
                out.add(MdText(inline(para.toString().trim()), MdKind.PARA))
                para.clear()
            }
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            if (t.startsWith("```")) {
                flush()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                var first = true
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (!first) code.append('\n')
                    code.append(lines[i]); first = false; i++
                }
                out.add(MdCode(lang, code.toString()))
                i++
                continue
            }
            val h = heading.find(t)
            val b = bullet.find(t)
            val n = numbered.find(t)
            when {
                t.isEmpty() -> flush()
                h != null -> {
                    flush()
                    val level = h.groupValues[1].length
                    out.add(MdText(inline(h.groupValues[2]), if (level <= 1) MdKind.H1 else MdKind.H2))
                }
                b != null -> { flush(); out.add(MdText(inline(b.groupValues[1]), MdKind.BULLET, "•")) }
                n != null -> { flush(); out.add(MdText(inline(n.groupValues[2]), MdKind.NUM, n.groupValues[1] + ".")) }
                t.startsWith(">") -> { flush(); out.add(MdText(inline(t.removePrefix(">").trim()), MdKind.QUOTE)) }
                else -> {
                    if (para.isNotEmpty()) para.append('\n')
                    para.append(line.trimEnd())
                }
            }
            i++
        }
        flush()
        return out
    }
}
