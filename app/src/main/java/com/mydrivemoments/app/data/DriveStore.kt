package com.mydrivemoments.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Keeps visits and saved places on the head unit only (SharedPreferences, JSON).
 * Nothing leaves the vehicle.
 */
class DriveStore(context: Context) {
    private val prefs = context.getSharedPreferences("drive_store", Context.MODE_PRIVATE)

    fun loadVisits(): List<Visit> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_VISITS, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).toVisit() }
    }.getOrDefault(emptyList())

    fun saveVisits(list: List<Visit>) {
        prefs.edit().putString(KEY_VISITS, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun loadSaved(): List<SavedPlace> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_SAVED, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).toSaved() }
    }.getOrDefault(emptyList())

    fun saveSaved(list: List<SavedPlace>) {
        prefs.edit().putString(KEY_SAVED, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    /**
     * Sample history for the demo, laid out relative to [today] so the calendar is never empty and a
     * routine can be discovered live: one visit to 이마트 성수점 exactly a week ago means arriving there
     * again today issues a routine stamp.
     */
    fun seedIfEmpty(today: LocalDate) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val lastMon = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY))
        val lastFri = today.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
        val visits = listOf(
            Visit("seed1", lastMon.minusWeeks(1), LocalTime.of(8, 44), "스타벅스 성수역점", "서울 성동구 아차산로", 37.5446, 127.0559, 3.1, 11, false),
            Visit("seed2", lastMon, LocalTime.of(8, 41), "스타벅스 성수역점", "서울 성동구 아차산로", 37.5446, 127.0559, 3.1, 10, false),
            Visit("seed3", today.minusWeeks(1), LocalTime.of(18, 37), "이마트 성수점", "서울 성동구 뚝섬로 379", 37.5397, 127.0532, 2.4, 9, false),
            Visit("seed4", lastFri.minusWeeks(1), LocalTime.of(19, 20), "카페 온유 양평", "경기 양평군 양서면", 37.5350, 127.3300, 48.6, 62, false, "노을 맛집", true),
            Visit("seed5", lastFri.minusWeeks(1).plusDays(1), LocalTime.of(14, 5), "남한산성 성곽길", "경기 광주시 남한산성면", 37.4787, 127.1835, 24.8, 41, true, "가족 나들이", true),
            Visit("seed6", today.minusDays(5), LocalTime.of(19, 2), "성수 크로스핏", "서울 성동구 성수이로", 37.5440, 127.0500, 1.8, 7, false),
        ).filter { it.date.isBefore(today) }
        val saved = listOf(
            SavedPlace("두물머리 물래길", "경기 양평군 양서면 양수리", 37.5432, 127.3155, "카페 온유에서 2.1km"),
            SavedPlace("양평 레일바이크", "경기 양평군 용문면", 37.4918, 127.4880, "카페 온유와 같은 방향"),
            SavedPlace("서울숲 은행나무길", "서울 성동구 뚝섬로 273", 37.5444, 127.0374, "회사에서 2.9km"),
        )
        saveVisits(visits)
        saveSaved(saved)
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    private fun Visit.toJson() = JSONObject().apply {
        put("id", id); put("date", date.toString()); put("time", time.toString())
        put("name", name); put("address", address)
        lat?.let { put("lat", it) }; lon?.let { put("lon", it) }
        put("km", distanceKm); put("min", minutes); put("pax", withPassenger)
        tag?.let { put("tag", it) }; put("fav", fav)
    }

    private fun JSONObject.toVisit() = Visit(
        id = getString("id"),
        date = LocalDate.parse(getString("date")),
        time = LocalTime.parse(getString("time")),
        name = getString("name"),
        address = optString("address"),
        lat = if (has("lat")) getDouble("lat") else null,
        lon = if (has("lon")) getDouble("lon") else null,
        distanceKm = optDouble("km", 0.0),
        minutes = optInt("min", 0),
        withPassenger = optBoolean("pax", false),
        tag = if (has("tag")) getString("tag") else null,
        fav = optBoolean("fav", false),
    )

    private fun SavedPlace.toJson() = JSONObject().apply {
        put("name", name); put("address", address); put("lat", lat); put("lon", lon); put("note", note)
    }

    private fun JSONObject.toSaved() = SavedPlace(
        getString("name"), optString("address"), getDouble("lat"), getDouble("lon"), optString("note"),
    )

    private companion object {
        const val KEY_VISITS = "visits"
        const val KEY_SAVED = "saved"
        const val KEY_SEEDED = "seeded"
    }
}
