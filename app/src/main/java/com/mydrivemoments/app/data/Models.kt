package com.mydrivemoments.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Rough place category, used only to pick the illustration on a drive card. */
enum class PlaceKind { CAFE, MART, MOUNTAIN, RIVER, CITY }

fun kindOf(name: String): PlaceKind = when {
    listOf("카페", "커피", "스타벅스", "베이커리").any { name.contains(it) } -> PlaceKind.CAFE
    listOf("마트", "시장", "몰", "백화점").any { name.contains(it) } -> PlaceKind.MART
    listOf("산", "성곽", "숲", "공원", "수목원").any { name.contains(it) } -> PlaceKind.MOUNTAIN
    listOf("강", "머리", "호수", "바다", "해변", "물래", "계곡").any { name.contains(it) } -> PlaceKind.RIVER
    else -> PlaceKind.CITY
}

/** Record: one arrival, saved automatically from the navigation arrival event. */
data class Visit(
    val id: String,
    val date: LocalDate,
    val time: LocalTime,
    val name: String,
    val address: String,
    val lat: Double?,
    val lon: Double?,
    val distanceKm: Double,
    val minutes: Int,
    val withPassenger: Boolean,
    val tag: String? = null,
    val fav: Boolean = false,
) {
    val kind: PlaceKind get() = kindOf(name)
    val short: String get() = name.split(" ", "·").first().trim().take(4)
}

/** ★ 다시 가고 싶은 곳 — feeds Recommend. */
data class SavedPlace(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val note: String,
    val fresh: Boolean = false,
)

/** Discover: the same place on the same weekday at least twice → a routine stamp. */
data class Routine(val placeName: String, val day: DayOfWeek, val count: Int) {
    val dayLabel: String get() = day.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    val label: String
        get() = when (kindOf(placeName)) {
            PlaceKind.MART -> "${dayLabel}요일 장보기"
            PlaceKind.CAFE -> "${dayLabel}요일 커피"
            PlaceKind.MOUNTAIN, PlaceKind.RIVER -> "${dayLabel}요일 드라이브"
            PlaceKind.CITY -> "${dayLabel}요일 루틴"
        }
}

data class Recommendation(val place: SavedPlace, val reason: String, val roundTripKm: Int?)

val TAGS = listOf("가족 나들이", "가을 드라이브", "다시 올 곳", "노을 맛집")

fun findRoutines(visits: List<Visit>, today: LocalDate): List<Routine> =
    visits.filter { !it.date.isBefore(today.minusDays(35)) }
        .groupBy { it.name to it.date.dayOfWeek }
        .filter { it.value.size >= 2 }
        .map { (k, v) -> Routine(k.first, k.second, v.size) }
        .sortedByDescending { it.count }

fun isRoutineVisit(v: Visit, routines: List<Routine>) =
    routines.any { it.placeName == v.name && it.day == v.date.dayOfWeek }

/** Straight-line distance in km (haversine). */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}
