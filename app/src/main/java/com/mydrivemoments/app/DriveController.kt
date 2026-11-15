package com.mydrivemoments.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mydrivemoments.app.data.DriveStore
import com.mydrivemoments.app.data.Recommendation
import com.mydrivemoments.app.data.Routine
import com.mydrivemoments.app.data.SavedPlace
import com.mydrivemoments.app.data.Visit
import com.mydrivemoments.app.data.distanceKm
import com.mydrivemoments.app.data.findRoutines
import com.mydrivemoments.app.sdk.Destination
import com.mydrivemoments.app.sdk.GearState
import com.mydrivemoments.app.sdk.NaviBridge
import com.mydrivemoments.app.sdk.VehicleBridge
import ai.pleos.playground.navi.data.BookmarkInfo
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

enum class HomeTab { CALENDAR, COLLECTION }

private val DEMO_ORIGIN = 37.5446 to 127.0559

data class LogLine(val time: String, val text: String)

/**
 * App state and the Record → Discover / Collect → Recommend flow.
 * Vehicle and navigation events come in through the two SDK bridges.
 */
class DriveController(context: Context) {
    private val store = DriveStore(context)

    var gear by mutableStateOf(GearState.UNKNOWN); private set
    var rearOccupied by mutableStateOf(false); private set
    var rangeKm by mutableStateOf<Int?>(null); private set
    var destination by mutableStateOf<Destination?>(null); private set
    var pendingVisit by mutableStateOf<Visit?>(null); private set
    var visits by mutableStateOf(listOf<Visit>()); private set
    var saved by mutableStateOf(listOf<SavedPlace>()); private set
    var routines by mutableStateOf(listOf<Routine>()); private set
    var newRoutine by mutableStateOf<Routine?>(null); private set
    var revealId by mutableStateOf<String?>(null); private set
    var lastCardId by mutableStateOf<String?>(null); private set
    var recommendation by mutableStateOf<Recommendation?>(null); private set
    var homeTab by mutableStateOf(HomeTab.CALENDAR)
    var demoToolsOpen by mutableStateOf(false)
    val log = mutableStateListOf<LogLine>()

    private var bookmarks: List<BookmarkInfo> = emptyList()
    private var here: Pair<Double, Double>? = null

    private val vehicle = VehicleBridge(context, ::onGear, ::onRear, ::onRange, ::addLog)
    private val navi = NaviBridge(context, ::onDestination, ::onArrived, ::onBookmarks, ::onLocation, ::addLog)

    fun start() {
        loadData()
        vehicle.start()
        navi.start()
    }

    private fun loadData() {
        store.seedIfEmpty(LocalDate.now())
        visits = store.loadVisits()
        saved = store.loadSaved()
        routines = findRoutines(visits, LocalDate.now())
        refreshRecommendation()
    }

    fun stop() {
        vehicle.stop()
        navi.stop()
    }

    // ---------------- Record ----------------

    private fun onDestination(d: Destination?) {
        destination = d
        if (d != null) addLog("이동 기록 시작 · ${d.name}")
    }

    private fun onArrived(name: String, address: String, distanceM: Int, durationS: Int) {
        val dest = destination
        val placeName = name.ifBlank { dest?.name ?: "도착한 장소" }
        val km = when {
            distanceM > 0 -> distanceM / 1000.0
            dest?.lat != null && here != null -> distanceKm(here!!.first, here!!.second, dest.lat, dest.lon!!)
            else -> 0.0
        }
        pendingVisit = Visit(
            id = UUID.randomUUID().toString(),
            date = LocalDate.now(),
            time = LocalTime.now().withSecond(0).withNano(0),
            name = placeName,
            address = address.ifBlank { dest?.address ?: "" },
            lat = dest?.lat, lon = dest?.lon,
            distanceKm = (km * 10).roundToInt() / 10.0,
            minutes = (durationS / 60).coerceAtLeast(if (km > 0) 1 else 0),
            withPassenger = rearOccupied,
        )
        addLog("방문 기록 저장 대기 · $placeName (주차하면 확정)")
        if (gear == GearState.PARK) commitArrival()
    }

    private fun onGear(g: GearState) {
        val before = gear
        gear = g
        when (g) {
            GearState.PARK -> {
                if (pendingVisit != null) commitArrival() else if (before != GearState.PARK) refreshRecommendation()
            }
            GearState.DRIVE -> {
                // Safety: nothing interactive while driving.
                if (revealId != null) { lastCardId = revealId; revealId = null; addLog("주행 시작 · 카드 화면 닫음") }
                newRoutine = null
            }
            else -> Unit
        }
    }

    private fun commitArrival() {
        val v = pendingVisit ?: return
        pendingVisit = null
        val before = routines
        visits = visits + v
        store.saveVisits(visits)
        routines = findRoutines(visits, LocalDate.now())
        destination = null
        addLog("Record · ${v.name} · ${v.distanceKm}km 기록 완료")
        addLog("Collect · 드라이브 카드 자동 생성")
        val found = routines.firstOrNull { r -> r.placeName == v.name && r.day == v.date.dayOfWeek && before.none { it.placeName == r.placeName && it.day == r.day } }
        if (found != null) {
            newRoutine = found
            addLog("Discover · 루틴 스탬프 발급 · ${found.label} (${found.count}회)")
        }
        revealId = v.id
        homeTab = HomeTab.CALENDAR
        refreshRecommendation()
    }

    // ---------------- Collect ----------------

    fun visit(id: String?) = visits.firstOrNull { it.id == id }

    fun setTag(id: String, tag: String) {
        visits = visits.map { if (it.id == id) it.copy(tag = tag) else it }
        store.saveVisits(visits)
        addLog("태그 · #$tag")
    }

    fun toggleFav(id: String) {
        val v = visit(id) ?: return
        val nowFav = !v.fav
        visits = visits.map { if (it.id == id) it.copy(fav = nowFav) else it }
        store.saveVisits(visits)
        if (nowFav && v.lat != null && v.lon != null) {
            val note = "다녀옴 · ${v.date.monthValue}/${v.date.dayOfMonth}"
            saved = listOf(SavedPlace(v.name, v.address, v.lat, v.lon, note, fresh = true)) + saved.filter { it.name != v.name }
            store.saveSaved(saved)
        }
        addLog(if (nowFav) "★ 다시 가고 싶은 곳 · ${v.name} → 추천 후보" else "★ 해제 · ${v.name}")
        refreshRecommendation()
    }

    fun closeCard() {
        lastCardId = revealId
        revealId = null
    }

    fun reopenCard() {
        if (gear == GearState.DRIVE) return
        revealId = lastCardId
    }

    fun dismissRoutine() { newRoutine = null }

    // ---------------- Recommend ----------------

    private fun onBookmarks(list: List<BookmarkInfo>) {
        bookmarks = list
        refreshRecommendation()
    }

    private fun onLocation(lat: Double, lon: Double) {
        here = lat to lon
        refreshRecommendation()
    }

    private fun onRear(occupied: Boolean) { rearOccupied = occupied }

    private fun onRange(km: Int) {
        val changed = rangeKm != km
        rangeKm = km
        if (changed) refreshRecommendation()
    }

    fun refreshRecommendation() {
        val today = LocalDate.now()
        val recent = visits.filter { !it.date.isBefore(today.minusDays(7)) }.map { it.name }.toSet()
        val fromBookmarks = bookmarks.mapNotNull { b ->
            val n = b.name ?: return@mapNotNull null
            SavedPlace(n, b.address ?: "", b.latitude, b.longitude, "내비 즐겨찾기")
        }
        val candidates = (saved + fromBookmarks).distinctBy { it.name }.filter { it.name !in recent }
        val origin = here
        val range = rangeKm
        val pick = candidates.firstOrNull { c ->
            if (origin == null || range == null) true
            else distanceKm(origin.first, origin.second, c.lat, c.lon) * 2 * 1.3 <= range
        } ?: run { recommendation = null; return }

        val nearFav = visits.filter { it.fav && it.lat != null && it.name != pick.name }
            .map { it to distanceKm(it.lat!!, it.lon!!, pick.lat, pick.lon) }
            .filter { it.second < 5.0 }
            .minByOrNull { it.second }
        val reason = when {
            nearFav != null -> "지난번 다녀온 ${nearFav.first.name} 근처(${"%.1f".format(nearFav.second)}km)에 저장해 둔 장소예요."
            range != null && origin != null -> "지금 주행 가능 거리로 다녀올 수 있는 저장 장소예요."
            else -> "저장해 둔 장소예요."
        }
        val roundTrip = origin?.let { (distanceKm(it.first, it.second, pick.lat, pick.lon) * 2).roundToInt() }
        if (recommendation?.place?.name != pick.name) addLog("Recommend · ${pick.name}")
        recommendation = Recommendation(pick, reason, roundTrip)
    }

    fun acceptRecommendation() {
        val r = recommendation ?: return
        val ok = navi.requestRoute(r.place.name, r.place.address, r.place.lat, r.place.lon)
        destination = Destination(r.place.name, r.place.address, r.place.lat, r.place.lon)
        addLog(if (ok) "목적지 설정 요청 · ${r.place.name}" else "목적지 설정(앱 내부) · ${r.place.name}")
        recommendation = null
    }

    fun laterRecommendation() {
        recommendation = null
        addLog("추천 보관 · 다음 주차 때 다시 보여주기")
    }

    // ---------------- Demo tools (for recording the demo on the emulator) ----------------

    fun demoArrive() {
        val d = destination ?: saved.firstOrNull()?.let { Destination(it.name, it.address, it.lat, it.lon) }
            ?: Destination("이마트 성수점", "서울 성동구 뚝섬로 379", 37.5397, 127.0532)
        if (destination == null) destination = d
        addLog("[시연] 도착 이벤트 재현 · ${d.name}")
        // Without a GPS fix on the emulator, measure from a default start point (성수동 office).
        val from = here ?: DEMO_ORIGIN
        val km = if (d.lat != null) distanceKm(from.first, from.second, d.lat, d.lon!!) * 1.3 else 5.0
        onArrived(d.name, d.address, (km * 1000).toInt(), maxOf(km * 85, 300.0).toInt())
    }

    fun demoGear(g: GearState) { addLog("[시연] 기어 ${if (g == GearState.PARK) "P" else "D"}"); onGear(g) }
    fun demoRear() { addLog("[시연] 뒷좌석 탑승 전환"); onRear(!rearOccupied) }
    fun demoDestination(name: String, address: String, lat: Double, lon: Double) {
        addLog("[시연] 목적지 설정 · $name")
        navi.requestRoute(name, address, lat, lon)
        onDestination(Destination(name, address, lat, lon))
    }

    fun demoReset() {
        store.clear()
        revealId = null; lastCardId = null; newRoutine = null; pendingVisit = null; destination = null
        log.clear()
        loadData()
        addLog("[시연] 데이터 초기화")
    }

    private fun addLog(text: String) {
        log.add(0, LogLine(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), text))
        if (log.size > 40) log.removeRange(40, log.size)
    }
}
