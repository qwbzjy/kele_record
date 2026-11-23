package com.cola.record.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

object DateUtils {

    fun today(): String = LocalDate.now().toString()

    fun nowTime(): String {
        val now = LocalTime.now()
        return "%02d:%02d".format(now.hour, now.minute)
    }

    fun shift(date: String, days: Long): String =
        LocalDate.parse(date).plusDays(days).toString()

    fun displayDate(date: String): String {
        val d = LocalDate.parse(date)
        return "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
    }

    fun weekday(date: String): String {
        val d = LocalDate.parse(date)
        return "周" + "一二三四五六日"[d.dayOfWeek.value - 1]
    }

    fun displayWithWeek(date: String): String = "${displayDate(date)} ${weekday(date)}"

    fun shortLabel(date: String): String {
        val d = LocalDate.parse(date)
        return "${d.monthValue}/${d.dayOfMonth}"
    }

    fun isToday(date: String): Boolean = date == today()

    fun millisOf(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun dateFromMillis(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    fun minutesOfTime(t: String?): Int? {
        if (t.isNullOrBlank()) return null
        val parts = t.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** 跨天时间差（如 22:00 -> 07:10 返回 550 分钟） */
    fun durationMinutes(start: String?, end: String?): Int? {
        val s = minutesOfTime(start) ?: return null
        val e = minutesOfTime(end) ?: return null
        var d = e - s
        if (d < 0) d += 24 * 60
        return d
    }

    fun durationText(minutes: Int?): String {
        if (minutes == null || minutes <= 0) return "—"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}小时${m}分"
            h > 0 -> "${h}小时"
            else -> "${m}分钟"
        }
    }

    fun hourText(minutes: Float?): String {
        if (minutes == null || minutes <= 0f) return "—"
        return "%.1f 小时".format(minutes)
    }

    fun daysBetween(from: String, to: String): List<String> {
        val start = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        val list = mutableListOf<String>()
        var cur = start
        while (!cur.isAfter(end)) {
            list.add(cur.toString())
            cur = cur.plusDays(1)
            if (list.size > 400) break
        }
        return list
    }

    fun lastNDays(end: String, n: Int): List<String> =
        daysBetween(shift(end, -(n - 1).toLong()), end)

    fun weekRange(date: String): Pair<String, String> {
        val d = LocalDate.parse(date)
        val start = d.minusDays((d.dayOfWeek.value - 1).toLong())
        return start.toString() to start.plusDays(6).toString()
    }

    fun inRange(date: String, from: String, to: String): Boolean = date >= from && date <= to

    fun ageText(birthday: String?): String {
        if (birthday.isNullOrBlank()) return ""
        return runCatching {
            val birth = LocalDate.parse(birthday)
            val now = LocalDate.now()
            if (birth.isAfter(now)) return@runCatching ""
            var years = now.year - birth.year
            var months = now.monthValue - birth.monthValue
            var days = now.dayOfMonth - birth.dayOfMonth
            if (days < 0) {
                months -= 1
                days += birth.plusMonths(1).lengthOfMonth()
            }
            if (months < 0) {
                years -= 1
                months += 12
            }
            when {
                years > 0 -> "${years}岁${months}个月"
                months > 0 -> "${months}个月${days}天"
                else -> "${days}天"
            }
        }.getOrDefault("")
    }
}
