package com.mydrivemoments.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydrivemoments.app.DriveController
import com.mydrivemoments.app.HomeTab
import com.mydrivemoments.app.data.TAGS
import com.mydrivemoments.app.data.Visit
import com.mydrivemoments.app.data.isRoutineVisit
import com.mydrivemoments.app.sdk.GearState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val panelShape = RoundedCornerShape(16.dp)

@Composable
fun AppScreen(c: DriveController) {
    Column(Modifier.fillMaxSize().background(C.bg)) {
        TopBar(c)
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            val card = c.visit(c.revealId)
            when {
                c.gear == GearState.DRIVE -> DrivingScreen(c)
                card != null -> CardScreen(c, card)
                else -> HomeScreen(c)
            }
        }
        if (c.demoToolsOpen) DemoTools(c)
    }
}

@Composable
private fun TopBar(c: DriveController) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(15_000) } }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(22.dp).border(4.dp, C.accent, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).background(C.accent, CircleShape))
        }
        // Long-press-free toggle for the demo tools: tap the app name.
        Text("My Drive Moments", color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
            modifier = Modifier.clickable { c.demoToolsOpen = !c.demoToolsOpen })
        Spacer(Modifier.weight(1f))
        Text(now.format(DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)), color = C.muted, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        if (c.destination != null && c.gear == GearState.DRIVE) Pill("● 이동 기록 중", C.route)
        if (c.rearOccupied) Pill("뒷좌석 탑승")
        c.rangeKm?.let { Pill("주행 가능 ${it}km") }
        Pill("GEAR " + when (c.gear) { GearState.PARK -> "P"; GearState.DRIVE -> "D"; GearState.OTHER -> "R/N"; GearState.UNKNOWN -> "–" }, C.ink)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
}

@Composable
private fun Pill(text: String, color: Color = C.muted) {
    Text(text, color = color, fontSize = 13.sp, modifier = Modifier.background(C.panel2, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp))
}

@Composable
private fun Panel(modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(panelShape)
            .background(C.panel)
            .then(if (highlight) Modifier.border(2.dp, C.accent, panelShape) else Modifier)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun Kicker(text: String, color: Color = C.muted) =
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)

@Composable
private fun CarButton(text: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) C.accent else C.panel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (primary) C.accentInk else C.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

// ---------------- Driving: record only ----------------

@Composable
private fun DrivingScreen(c: DriveController) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("주행 중", color = C.ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(
            c.destination?.let { "${it.name}(으)로 가는 중이에요. 도착하면 자동으로 기록할게요." }
                ?: "운전 중에는 기록만 해요. 도착하면 자동으로 남겨 둘게요.",
            color = C.muted, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile("${c.visits.count { it.date.month == LocalDate.now().month }}곳", "이번 달 기록")
            StatTile("${c.routines.size}개", "루틴 스탬프")
            StatTile("${c.visits.size}장", "드라이브 카드")
        }
    }
}

@Composable
private fun StatTile(value: String, label: String) {
    Column(Modifier.clip(panelShape).background(C.panel).padding(horizontal = 32.dp, vertical = 18.dp)) {
        Text(value, color = C.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = C.muted, fontSize = 14.sp)
    }
}

// ---------------- Home: calendar / collection + side panel ----------------

@Composable
private fun HomeScreen(c: DriveController) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.weight(1.5f).fillMaxHeight()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tab("${YearMonth.now().monthValue}월 달력", c.homeTab == HomeTab.CALENDAR) { c.homeTab = HomeTab.CALENDAR }
                    Tab("컬렉션", c.homeTab == HomeTab.COLLECTION) { c.homeTab = HomeTab.COLLECTION }
                    Spacer(Modifier.weight(1f))
                    val month = c.visits.filter { YearMonth.from(it.date) == YearMonth.now() }
                    Text("기록 ${month.size}곳 · 주행 ${"%.1f".format(month.sumOf { it.distanceKm })}km", color = C.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (c.homeTab == HomeTab.CALENDAR) CalendarGrid(c) else CollectionGrid(c)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            c.newRoutine?.let { r ->
                Panel(Modifier.fillMaxWidth(), highlight = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Kicker("루틴 스탬프 · 새로 발급", C.accent)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            RoutineStamp(r.placeName.split(" ").first().take(4), 56.dp)
                            Column {
                                Text("‘${r.label}’", color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("${r.dayLabel}요일마다 ${r.placeName}에 ${r.count}번 다녀왔어요.", color = C.muted, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            c.recommendation?.let { r ->
                Panel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Kicker("다음 드라이브 추천", C.accent)
                        Text(r.place.name, color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${r.reason} 이번 주말에 가볼까요?", color = Color(0xFFD6DEE7), fontSize = 14.sp)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            c.routines.firstOrNull()?.let { Chip("루틴 · ${it.label}") }
                            r.roundTripKm?.let { rt -> Chip("왕복 ${rt}km" + (c.rangeKm?.let { " · 주행 가능 ${it}km" } ?: "")) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CarButton("목적지로 설정", primary = true, modifier = Modifier.weight(1f)) { c.acceptRecommendation() }
                            CarButton("나중에") { c.laterRecommendation() }
                        }
                    }
                }
            }
            c.visit(c.lastCardId)?.let { v ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MiniPolaroid(v.kind, v.variant, Modifier.width(64.dp), tilt = -4f)
                        Column(Modifier.weight(1f)) {
                            Kicker("새 드라이브 카드")
                            Text(v.name + if (v.fav) " ★" else "", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("${v.distanceKm}km · ${v.minutes}분" + (v.tag?.let { " · #$it" } ?: ""), color = C.muted, fontSize = 13.sp)
                        }
                        CarButton("카드 보기") { c.reopenCard() }
                    }
                }
            }
            c.routines.take(3).forEach { r ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RoutineStamp(r.placeName.split(" ").first().take(4), 44.dp)
                        Column {
                            Kicker("루틴 스탬프")
                            Text("‘${r.label}’ · ${r.placeName}", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("최근 5주 ${r.dayLabel}요일 ${r.count}번 반복", color = C.muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (c.saved.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kicker("★ 다시 가고 싶은 곳")
                    c.saved.take(4).forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.panel2)
                                .then(if (s.fresh) Modifier.border(1.5.dp, C.accent, RoundedCornerShape(8.dp)) else Modifier)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text("★ ${s.name}", color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.note, color = C.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tab(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        color = if (on) C.bg else C.muted,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) C.ink else C.panel2).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun Chip(text: String) {
    Text(text, color = C.muted, fontSize = 12.sp, modifier = Modifier.background(C.panel2, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
}

val Visit.variant: Int get() = date.dayOfMonth % 3

@Composable
private fun CalendarGrid(c: DriveController) {
    val ym = YearMonth.now()
    val today = LocalDate.now()
    val lead = ym.atDay(1).dayOfWeek.value % 7 // Sunday first
    val cells = lead + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                Text(d, color = if (i == 0) Color(0xFFD98378) else C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(rows) { r ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { col ->
                        val idx = r * 7 + col - lead + 1
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (idx in 1..ym.lengthOfMonth()) {
                                val date = ym.atDay(idx)
                                val vs = c.visits.filter { it.date == date }
                                DayCell(idx, date == today, vs, c)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp, 14.dp).background(C.paper)); Text("  드라이브 카드 (방문마다 자동 생성)", color = C.muted, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).border(1.5.dp, C.stamp, CircleShape)); Text("  루틴 스탬프 (반복 패턴 발견)", color = C.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, today: Boolean, vs: List<Visit>, c: DriveController) {
    val routine = vs.firstOrNull { isRoutineVisit(it, c.routines) }
    val last = vs.lastOrNull()
    BoxWithConstraints(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(C.panel2)
            .then(if (today) Modifier.border(2.dp, C.accent, RoundedCornerShape(8.dp)) else Modifier)
            .padding(4.dp)
    ) {
        Text("$day", color = if (vs.isNotEmpty()) C.ink else C.muted, fontSize = 11.sp)
        val markSize = (minOf(maxWidth, maxHeight) * 0.62f)
        when {
            routine != null -> RoutineStamp(routine.short, markSize, Modifier.align(Alignment.BottomEnd))
            last != null -> MiniPolaroid(last.kind, last.variant, Modifier.align(Alignment.BottomEnd).width(markSize * 0.85f))
        }
        if (vs.size > 1) Text("+${vs.size - 1}", color = C.muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun CollectionGrid(c: DriveController) {
    val list = c.visits.sortedWith(compareByDescending<Visit> { it.date }.thenByDescending { it.time })
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(4.dp),
    ) {
        items(list, key = { it.id }) { v ->
            Box {
                Column(
                    Modifier.rotate(if (v.date.dayOfMonth % 2 == 0) 1.4f else -1.5f).shadow(4.dp).background(C.paper)
                        .clickable { c.homeTab = HomeTab.COLLECTION }
                        .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 7.dp)
                ) {
                    PlaceScene(v.kind, v.variant, Modifier.fillMaxWidth().aspectRatio(1.15f))
                    Text(v.short + if (v.fav) " ★" else "", color = C.paperInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                    Text("${v.date.monthValue}/${v.date.dayOfMonth} · ${v.distanceKm}km" + (v.tag?.let { " · #$it" } ?: ""), color = C.paperMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isRoutineVisit(v, c.routines)) RoutineStamp("루틴", 34.dp, Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
    }
}

// ---------------- Collect: auto-generated drive card (parked only) ----------------

@Composable
private fun CardScreen(c: DriveController, v: Visit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(0.9f).fillMaxHeight().clip(panelShape).background(C.panel), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight(0.92f).aspectRatio(0.82f).rotate(-2f).shadow(10.dp).background(C.paper).padding(14.dp)) {
                Column {
                    PlaceScene(v.kind, v.variant, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(
                        v.tag?.let { "$it · ${v.date.monthValue}/${v.date.dayOfMonth}" }
                            ?: "${v.date.monthValue}/${v.date.dayOfMonth} ${v.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.KOREAN)}요일의 드라이브",
                        color = C.paperInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp),
                    )
                    Text("${v.date} ${v.time} 도착 · ${v.name} · ${v.distanceKm}km · ${v.minutes}분", color = C.paperMuted, fontSize = 12.sp)
                }
                v.tag?.let {
                    Text("#$it", color = C.accentInk, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.TopStart).rotate(-5f).background(C.accent).padding(horizontal = 12.dp, vertical = 5.dp))
                }
                if (v.fav) Text("★", color = C.accent, fontSize = 34.sp, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Panel(Modifier.weight(1.35f).fillMaxHeight()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("오늘의 드라이브 카드", color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("주차하면 자동으로 만들어져요", color = C.muted, fontSize = 13.sp)
                }
                Text("${v.name} 방문이 컬렉션에 추가됐어요.", color = C.ink, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Fact(v.time.toString(), "도착", Modifier.weight(1f))
                    Fact("${v.distanceKm}km", "주행거리", Modifier.weight(1f))
                    Fact("${v.minutes}분", "주행 시간", Modifier.weight(1f))
                    Fact(if (v.withPassenger) "함께" else "혼자", "탑승", Modifier.weight(1f))
                }
                c.newRoutine?.takeIf { it.placeName == v.name }?.let { r ->
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(C.panel2).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoutineStamp(v.short, 48.dp)
                        Text("반복 방문을 발견해 ‘${r.label}’ 루틴 스탬프를 찍었어요.", color = C.ink, fontSize = 15.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("태그 · 탭 한 번 (선택)", color = C.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (v.withPassenger) Text("뒷좌석 탑승 감지", color = C.accent, fontSize = 13.sp)
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TAGS.forEachIndexed { i, t ->
                            val on = v.tag == t
                            Text(
                                "#$t" + if (i == 0 && v.withPassenger && !on) "  추천" else "",
                                color = if (on) C.accentInk else C.accent, fontSize = 16.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) C.accent else C.panel2)
                                    .border(1.dp, if (on) C.accent else Color(0xFF4A3A26), RoundedCornerShape(50))
                                    .clickable { c.setTag(v.id, t) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CarButton(if (v.fav) "★ 다시 가고 싶은 곳에 저장됨" else "★ 다시 가고 싶은 곳", primary = true, modifier = Modifier.weight(1f)) { c.toggleFav(v.id) }
                    CarButton("달력으로") { c.closeCard() }
                }
                Text("주차 중에만 보여요. 출발하면 자동으로 닫혀요.", color = C.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Fact(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(C.panel2).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = C.muted, fontSize = 12.sp)
    }
}

// ---------------- Demo tools: lets the demo be recorded on the emulator ----------------

@Composable
private fun DemoTools(c: DriveController) {
    Row(
        Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF080C10)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("시연 도구 (개발용) · 앱 이름을 누르면 닫혀요", color = C.muted, fontSize = 12.sp)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoButton("목적지: 두물머리") { c.demoDestination("두물머리 물래길", "경기 양평군 양서면 양수리", 37.5432, 127.3155) }
                DemoButton("목적지: 이마트 성수점") { c.demoDestination("이마트 성수점", "서울 성동구 뚝섬로 379", 37.5397, 127.0532) }
                DemoButton("기어 D") { c.demoGear(GearState.DRIVE) }
                DemoButton("도착 이벤트") { c.demoArrive() }
                DemoButton("기어 P") { c.demoGear(GearState.PARK) }
                DemoButton("뒷좌석 탑승") { c.demoRear() }
                DemoButton("데이터 초기화") { c.demoReset() }
            }
            Text("실제 기어·탑승·주행 가능 거리는 Virtual Console에서 바꿔도 반영돼요.", color = C.muted, fontSize = 11.sp)
        }
        LazyColumn(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(C.panel).padding(8.dp)) {
            items(c.log) { l ->
                Text("${l.time}  ${l.text}", color = if (l.text.startsWith("NaviHelper") || l.text.startsWith("Gear") || l.text.startsWith("Seat") || l.text.startsWith("Odometer")) C.route else C.ink,
                    fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun DemoButton(text: String, onClick: () -> Unit) {
    Text(text, color = C.ink, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.panel2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Suppress("unused")
private val unusedSp: TextUnit = 0.sp
