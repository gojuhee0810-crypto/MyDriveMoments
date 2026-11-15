package com.mydrivemoments.app.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener

enum class GearState { PARK, DRIVE, OTHER, UNKNOWN }

/**
 * Pleos Vehicle SDK wrapper.
 * - Gear: P → commit the arrival and show the drive card; D → hide anything interactive.
 * - Seat occupancy (2nd row): suggests the "가족 나들이" tag.
 * - Range remaining (Odometer): keeps recommendations within reach.
 * All callbacks are delivered on the main thread.
 */
class VehicleBridge(
    context: Context,
    private val onGear: (GearState) -> Unit,
    private val onRearOccupied: (Boolean) -> Unit,
    private val onRangeKm: (Int) -> Unit,
    private val log: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context)
    private val rearAreas = listOf(SeatArea(SeatValue.ROW_2_LEFT), SeatArea(SeatValue.ROW_2_RIGHT))
    private val rearState = BooleanArray(rearAreas.size)
    private val seatListeners = mutableListOf<Pair<SeatArea, SeatOccupancyListener>>()
    private var started = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = main.post { deliverGear(gear) }.let { }
        override fun onFailed(e: Exception) = main.post { log("Gear 오류 · ${e.message}") }.let { }
    }

    private val rangeListener = object : RangeRemainingListener {
        override fun onRangeRemainingChanged(meters: Float?) = main.post { deliverRange(meters) }.let { }
        override fun onFailed(e: Exception) = main.post { log("Range 오류 · ${e.message}") }.let { }
    }

    fun start() {
        try {
            vehicle.initialize()
            started = true
            log("Vehicle SDK 연결")
        } catch (e: Exception) {
            log("Vehicle SDK 연결 실패 · ${e.message}")
            return
        }
        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState({ g -> main.post { deliverGear(g) } }, { e -> main.post { log("기어 조회 실패 · ${e.message}") } })
        runCatching { gear.registerGearSelection(gearListener) }.onFailure { log("기어 구독 실패 · ${it.message}") }

        val odometer = vehicle.getOdometer()
        odometer.getRangeRemainingDistance({ m -> main.post { deliverRange(m) } }, { e -> main.post { log("주행 가능 거리 조회 실패 · ${e.message}") } })
        runCatching { odometer.registerRangeRemainingListener(rangeListener) }.onFailure { log("주행 가능 거리 구독 실패 · ${it.message}") }

        val seat = vehicle.getSeat()
        rearAreas.forEachIndexed { i, area ->
            val l = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupied: Boolean?) = main.post { deliverSeat(i, occupied == true) }.let { }
                override fun onFailed(e: Exception) = main.post { log("좌석 오류 · ${e.message}") }.let { }
            }
            seat.getCurrentSeatOccupancy(area, { o -> main.post { deliverSeat(i, o == true) } }, { e -> main.post { log("좌석 조회 실패 · ${e.message}") } })
            runCatching { seat.registerSeatOccupancy(area, l); seatListeners += area to l }
                .onFailure { log("좌석 구독 실패 · ${it.message}") }
        }
    }

    fun stop() {
        if (!started) return
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.getOdometer().unregisterRangeRemainingListener(rangeListener) }
        seatListeners.forEach { (a, l) -> runCatching { vehicle.getSeat().unregisterSeatOccupancy(a, l) } }
        seatListeners.clear()
        runCatching { vehicle.release() }
        started = false
    }

    private fun deliverGear(g: VehicleGear?) {
        val s = when (g) {
            VehicleGear.GEAR_PARK -> GearState.PARK
            VehicleGear.GEAR_DRIVE -> GearState.DRIVE
            null, VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
            else -> GearState.OTHER
        }
        log("Gear · ${g?.name ?: "UNKNOWN"}")
        onGear(s)
    }

    private fun deliverRange(meters: Float?) {
        if (meters == null) return
        val km = (meters / 1000f).toInt()
        log("Odometer · 주행 가능 거리 ${km}km")
        onRangeKm(km)
    }

    private fun deliverSeat(index: Int, occupied: Boolean) {
        rearState[index] = occupied
        log("Seat Occupancy · 2열 ${if (index == 0) "왼쪽" else "오른쪽"} ${if (occupied) "탑승" else "비어 있음"}")
        onRearOccupied(rearState.any { it })
    }
}
