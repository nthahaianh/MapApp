package com.example.mapapp

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.ErrorCode
import com.here.android.mpa.search.GeocodeRequest
import com.here.android.mpa.search.ReverseGeocodeRequest

class MapViewModel : ViewModel() {
    var isShowAddress: MutableLiveData<Boolean> = MutableLiveData()
    var isShowWay: MutableLiveData<Boolean> = MutableLiveData()
    var isShowSearch: MutableLiveData<Boolean> = MutableLiveData()
    var transport = MutableLiveData<Int>()
    var mapRouteList: MutableLiveData<ArrayList<MapObject>> = MutableLiveData()
    var resultWayShort: MutableLiveData<String> = MutableLiveData()
    var resultOtherWay: MutableLiveData<String> = MutableLiveData()
    private var startPoint: LatLng? = null
    private var endPoint: LatLng? = null
    private var mapRoute: MapRoute? = null
    private var myLocationMarker: MapMarker? = null
    private var destinationMarker: MapMarker? = null
    private var addressStart: String? = null
    private var addressDestination: String? = null
    private lateinit var map: Map

    init {
        isShowAddress.value = false
        isShowWay.value = false
        isShowSearch.value = false
        addressDestination = "Hanoi"
        resultWayShort.value = ""
        resultOtherWay.value = ""
        transport.value = 0
        mapRouteList.value = ArrayList()
    }

    fun initMap(mapFragment: AndroidXMapFragment) {
        mapFragment.init { error ->
            if (error == OnEngineInitListener.Error.NONE) {
                map = mapFragment.map!!
                map.projectionMode = Map.Projection.MERCATOR
                map.setCenter(
                    GeoCoordinate(MainActivity.latHN, MainActivity.lngHN),
                    Map.Animation.NONE
                )
                val level: Double = map.maxZoomLevel / 1.5                // Set the zoom level
                map.zoomLevel = level
                if (destinationMarker == null) {
                    addDestinationMarker(MainActivity.latHN, MainActivity.lngHN)
                    addressDestination = "lat/lng: (${MainActivity.latHN},${MainActivity.lngHN})\n$addressDestination"
                }
                mapFragment.mapGesture!!.addOnGestureListener(object :
                    MapGesture.OnGestureListener.OnGestureListenerAdapter() {
                    override fun onTapEvent(p: PointF): Boolean {
                        cleanWay()
                        val position = map.pixelToGeo(p)
                        if (position != null) {
                            addDestinationMarker(
                                position.latitude,
                                position.longitude
                            )
                            changeGeotoAdd(position)
                        }
                        return false
                    }

                    override fun onLongPressEvent(p: PointF): Boolean {
                        cleanWay()
                        val position = map.pixelToGeo(p)
                        if (position != null) {
                            addDestinationMarker(
                                position.latitude,
                                position.longitude
                            )
                            changeGeotoAdd(position)
                        }
                        return false
                    }
                }, 3, true)
            } else {
                Log.e("map-error", "init error")
            }
        }
    }

    fun getAddressStart(): String? {
        return addressStart
    }

    fun setAddressStart(newAddress: String) {
        addressStart = newAddress
    }

    fun getAddressDestination(): String? {
        return addressDestination
    }

    //convert from geocoordinate to address
    fun changeGeotoAdd(position: GeoCoordinate) {
        val request = ReverseGeocodeRequest(position)
        request.execute { location, error ->
            if (error !== ErrorCode.NONE) {
                Log.e("HERE", error.toString())
            } else {
                endPoint = LatLng(position.latitude, position.longitude)
                addressDestination = "$endPoint\n${location?.address}"
                isShowAddress.value = true
                isShowSearch.value = false
                isShowWay.value = false
            }
        }
    }

    //search location by string
    fun searchLocation(query: String) {
        val hanoi = GeoCoordinate(MainActivity.latHN, MainActivity.lngHN)
        val request = GeocodeRequest(query).setSearchArea(hanoi, 1000)
        request.execute { results, error ->
            if (error != ErrorCode.NONE) {
                Log.e("HERE", error.toString())
            } else {
                if (results != null && results.size > 0) {
                    val result = results[0]
                    result.location?.coordinate?.let {
                        addDestinationMarker(
                            it.latitude,
                            it.longitude
                        )
                    }
                    addressDestination = "$endPoint\n${result.location!!.address}"
                } else {
                    Log.e("search", "No result")
                }
            }
        }
    }

    fun addDestinationMarker(lat: Double, lng: Double) {
        if (destinationMarker != null) {
            map.removeMapObject(destinationMarker!!)
        }
        endPoint = LatLng(lat, lng)
        destinationMarker = MapMarker(GeoCoordinate(lat, lng))
        map.addMapObject(destinationMarker!!)
        map.setCenter(GeoCoordinate(lat, lng), Map.Animation.NONE)
    }

    fun addMyLocationMarker(lat: Double, lng: Double) {
        if (myLocationMarker != null) {
            map.removeMapObject(myLocationMarker!!)
        }
        startPoint = LatLng(lat, lng)
        myLocationMarker = MapMarker(GeoCoordinate(lat, lng))
        map.addMapObject(myLocationMarker!!)
        map.setCenter(GeoCoordinate(lat, lng), Map.Animation.NONE)
    }

    fun cleanWay() {    //remove old route
        if (mapRouteList.value!!.isNotEmpty()) {
            map.removeMapObjects(mapRouteList.value!!)
            mapRouteList.value?.clear()
        }
    }

    fun reversePosition() {
        destinationMarker?.let { map.removeMapObject(it) }
        myLocationMarker?.let { map.removeMapObject(it) }
        val mid = endPoint
        endPoint = startPoint
        startPoint = mid
        val string = addressDestination
        addressDestination = addressStart
        addressStart = string
        addMyLocationMarker(startPoint!!.latitude, startPoint!!.longitude)
        addDestinationMarker(endPoint!!.latitude, endPoint!!.longitude)
    }

    fun createRoute(count: Int, context: Context) {
        val routePlan = RoutePlan()
        routePlan.addWaypoint(
            RouteWaypoint(
                GeoCoordinate(
                    startPoint!!.latitude,
                    startPoint!!.longitude
                )
            )
        )
        routePlan.addWaypoint(
            RouteWaypoint(
                GeoCoordinate(
                    endPoint!!.latitude,
                    endPoint!!.longitude
                )
            )
        )
        val routeOptions = RouteOptions()
        if (transport.value == 0) {
            routeOptions.transportMode = RouteOptions.TransportMode.CAR
        } else {
            if (transport.value == 1) {
                routeOptions.transportMode = RouteOptions.TransportMode.BICYCLE
            } else {
                routeOptions.transportMode = RouteOptions.TransportMode.PEDESTRIAN
            }
        }
        if (count == 1) {
            routeOptions.routeType = RouteOptions.Type.SHORTEST
        } else {
            routeOptions.routeType = RouteOptions.Type.FASTEST
        }
        routePlan.routeOptions = routeOptions

        val mRouter = CoreRouter()
        mRouter.calculateRoute(routePlan,
            object : Router.Listener<List<RouteResult>, RoutingError> {
                override fun onProgress(i: Int) {
                }

                override fun onCalculateRouteFinished(
                    routeResults: List<RouteResult>,
                    routingError: RoutingError
                ) {
                    if (routingError == RoutingError.NONE) {
                        val route = routeResults[0].route
                        mapRoute = MapRoute(route)
                        if (count == 1) {
                            mapRoute!!.color = Color.RED
                        } else {
                            mapRoute!!.color = Color.GREEN
                        }
                        mapRouteList.value?.add(mapRoute!!)
                        map.addMapObject(mapRoute!!)
                        map.setCenter(
                            GeoCoordinate(startPoint!!.latitude, startPoint!!.longitude),
                            Map.Animation.NONE
                        )
                        map.zoomLevel = map.maxZoomLevel / 1.4f
                        var result =
                            "Time: ${formatTime(route.getTtaExcludingTraffic(Route.WHOLE_ROUTE)!!.duration)}\n"
                        result += "Distance: ${mapRoute!!.route!!.length / 1000.0}km\n"
                        Log.e("infoOfWay", result)
                        if (count == 1) {
                            resultWayShort.value = result
                        } else {
                            resultOtherWay.value = result
                        }
                        isShowWay.value = true
                    } else {
                        Log.e("infor", "onCalculateRouteFinished: $routingError")
                        Toast.makeText(context, "Can't find the way", Toast.LENGTH_SHORT).show()
                        isShowWay.value = false
                    }
                }
            })
    }

    private fun formatTime(string: Int): String {
        var result = ""
        var second = string
        val hours = second / 3600
        second -= hours * 3600
        val minutes = second / 60
        second -= minutes * 60
        if (hours > 0) {
            result += "${hours}h "
        }
        if (minutes > 0) {
            result += " ${minutes}min"
        }
        return result
    }
}