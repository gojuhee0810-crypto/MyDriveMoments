package com.mydrivemoments.app.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlin.math.absoluteValue

data class Destination(val name: String, val address: String, val lat: Double?, val lon: Double?)

/**
 * Pleos NaviHelper SDK wrapper.
 * - onDestinationChanged / onDestinationInfo: what the driver is heading to (Record starts here).
 * - onDestinationArrived: the arrival event that creates a visit record.
 * - onBookmarkInfo: navigation favourites, extra candidates for Recommend.
 * - requestRoute(): sends a recommended place to the navigation app.
 */
class NaviBridge(
    context: Context,
    private val onDestination: (Destination?) -> Unit,
    private val onArrived: (name: String, address: String, distanceM: Int, durationS: Int) -> Unit,
    private val onBookmarks: (List<BookmarkInfo>) -> Unit,
    private val onLocation: (Double, Double) -> Unit,
    private val log: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val helper = NaviHelper(context)
    private var ready = false

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationChanged() {
            main.post { log("NaviHelper · onDestinationChanged()") }
            runCatching { helper.getDestinationInfo() }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            main.post {
                log("NaviHelper · onDestinationInfo() · ${destinationInfo.name}")
                val name = destinationInfo.name
                onDestination(
                    if (name.isNullOrBlank()) null
                    else Destination(name, destinationInfo.address ?: "", destinationInfo.latitude, destinationInfo.longitude)
                )
            }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            main.post { log("NaviHelper · onRouteStarted() · ${routeStartInfo.name}") }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            main.post {
                log("NaviHelper · onDestinationArrived() · ${destinationArrivedInfo.name}")
                onArrived(
                    destinationArrivedInfo.name ?: "",
                    destinationArrivedInfo.address ?: "",
                    destinationArrivedInfo.distance,
                    destinationArrivedInfo.duration,
                )
            }
        }

        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
            main.post {
                log("NaviHelper · onBookmarkInfo() · ${bookmarkInfoList.size}곳")
                onBookmarks(bookmarkInfoList)
            }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            main.post { onLocation(currentLocationInfo.latitude, currentLocationInfo.longitude) }
        }

        override fun onError(errorCode: NaviErrorCode) {
            main.post { log("NaviHelper · onError() · ${errorCode.name}") }
        }
    }

    fun start() {
        try {
            helper.initialize(
                {
                    main.post {
                        ready = true
                        log("NaviHelper SDK 연결")
                        helper.addListener(listener)
                        refresh()
                    }
                },
                { t -> main.post { log("NaviHelper 연결 실패 · ${t.message}") } },
            )
        } catch (e: Exception) {
            log("NaviHelper 연결 실패 · ${e.message}")
        }
    }

    fun refresh() {
        if (!ready) return
        runCatching {
            helper.getDestinationInfo()
            helper.getBookmarkInfo()
            helper.getCurrentLocationInfo()
        }.onFailure { log("NaviHelper 조회 실패 · ${it.message}") }
    }

    fun requestRoute(name: String, address: String, lat: Double, lon: Double): Boolean {
        if (!ready) {
            log("NaviHelper 미연결 · 목적지 설정 건너뜀")
            return false
        }
        return runCatching {
            helper.requestRoute(
                RouteInfo(
                    latitude = lat,
                    longitude = lon,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = name,
                    poiId = "mdm-${name.hashCode().absoluteValue}",
                    poiSubId = "0",
                    address = address,
                )
            )
            log("NaviHelper · requestRoute() · $name")
            true
        }.getOrElse { log("목적지 설정 실패 · ${it.message}"); false }
    }

    fun stop() {
        if (ready) runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
        ready = false
    }
}
