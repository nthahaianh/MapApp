package com.example.mapapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.mapping.MapGesture.OnGestureListener.OnGestureListenerAdapter
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.ErrorCode
import com.here.android.mpa.search.GeocodeRequest
import com.here.android.mpa.search.ReverseGeocodeRequest
import kotlinx.android.synthetic.main.activity_main.*

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_LOCATION_PERMISSION = 1
    }

    private val latHN: Double = 21.03549853898585
    private val lngHN: Double = 105.83270413801074
    private lateinit var map: Map
    var destinationMarker: MapMarker? = null
    var myLocationMarker: MapMarker? = null
    private var startPoint: LatLng? = null
    private var endPoint: LatLng? = null
    private var mapRouteList: ArrayList<MapObject> = ArrayList()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    var addressDestination: String = "Hanoi"
    var addressStart: String = "Your position"
    var transport: String = "Bicycle"
    private var mapRoute: MapRoute? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissions()
        setUpMap()
        setUpView()
    }

    private fun setUpView() {
        map_ivNavigation.setOnClickListener {
            map_llAddress.visibility = View.GONE
            if (addressDestination == "") {
                Toast.makeText(baseContext, "Select a place", Toast.LENGTH_SHORT).show()
                addressDestination
            } else if (startPoint == null) {
                Toast.makeText(baseContext, "Please get current position", Toast.LENGTH_SHORT)
                    .show()
            } else {
                map_llSearch.visibility = View.GONE
                showDialog()
            }
        }
        map_ivSearch.setOnClickListener {
            map_llAddress.visibility = View.GONE
            map_llSearch.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
        }
        map_btnSearch.setOnClickListener {
            if (map_etSearch.text.toString() == "") {
                Toast.makeText(baseContext, "Address is empty", Toast.LENGTH_SHORT).show()
            } else {
                if (checkConnectivity()) {
                    map_llAddress.visibility = View.GONE
                    map_llSearch.visibility = View.GONE
                    scrollView.visibility = View.GONE
                    searchLocation(map_etSearch.text.toString())
                }
            }
        }
        map_ivMyLocation.setOnClickListener {
            map_llAddress.visibility = View.GONE
            map_llSearch.visibility = View.GONE
            scrollView.visibility = View.GONE
            getCurrentUserLocation()
        }
    }

    private fun setUpMap() {
        var mapFragment: AndroidXMapFragment =
            supportFragmentManager.findFragmentById(R.id.mapfragment) as AndroidXMapFragment
        mapFragment.init { error ->
            if (error == OnEngineInitListener.Error.NONE) {
                map = mapFragment.map!!
                map.projectionMode = Map.Projection.MERCATOR
                map.setCenter(GeoCoordinate(latHN, lngHN), Map.Animation.NONE)
                val level: Double = map.maxZoomLevel / 1.5                // Set the zoom level
                map.zoomLevel = level
                addDestinationMarker(latHN, lngHN)
                mapFragment.mapGesture!!.addOnGestureListener(object : OnGestureListenerAdapter() {
                    override fun onTapEvent(p: PointF): Boolean {
                        cleanWay()
                        val position = map.pixelToGeo(p)
                        if (position != null) {
                            addDestinationMarker(position.latitude, position.longitude)
                            changeGeotoAdd(position)
                        }
                        return false
                    }

                    override fun onLongPressEvent(p: PointF): Boolean {
                        cleanWay()
                        val position = map.pixelToGeo(p)
                        if (position != null) {
                            addDestinationMarker(position.latitude, position.longitude)
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

    //convert from geocoordinate to address
    fun changeGeotoAdd(position: GeoCoordinate) {
        val request = ReverseGeocodeRequest(position)
        request.execute { location, error ->
            if (error !== ErrorCode.NONE) {
                Log.e("HERE", error.toString())
            } else {
                endPoint = LatLng(position.latitude, position.longitude)
                addressDestination = "${location?.address}"
                map_tvAddress.text = "$endPoint\n $addressDestination"
                map_llAddress.visibility = View.VISIBLE
                map_llSearch.visibility = View.GONE
                scrollView.visibility = View.GONE
            }
        }
    }

    // Check internet
    private fun checkConnectivity(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = connectivityManager.activeNetworkInfo
        return if (info == null || !info.isConnected || !info.isAvailable) {
            Toast.makeText(baseContext, "No internet connection", Toast.LENGTH_SHORT).show()
            false
        } else {
            true
        }
        return false
    }

    // Request user for location access
    private fun requestPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_CODE_LOCATION_PERMISSION
            )
        }
    }

    // Call back function for location access
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && permissions[0] == Manifest.permission.ACCESS_FINE_LOCATION) {
                    if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                        Log.e("log", "1")
                    } else {
                        Log.e("log", "2")
                    }
                }
            }
        }
    }

    // Get current user location
    private fun getCurrentUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(baseContext, "Please allow permission", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    addressStart = "Your position"
                    addMyLocationMarker(location.latitude, location.longitude)
                } else {
                    Log.e("HEREmap", "My location null")
                }
            }
    }

    //search location by string
    private fun searchLocation(query: String) {
        val hanoi = GeoCoordinate(latHN, lngHN)
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
                    addressDestination = "${result.location!!.address}"
                } else {
                    Log.e("search", "No result")
                }
            }
        }
    }

    private fun addMyLocationMarker(lat: Double, lng: Double) {
        startPoint = LatLng(lat, lng)
        if (myLocationMarker != null) {
            map.removeMapObject(myLocationMarker!!)
        }
        myLocationMarker = MapMarker(GeoCoordinate(lat, lng))
        map.addMapObject(myLocationMarker!!)
        map.setCenter(GeoCoordinate(lat, lng), Map.Animation.NONE)
    }

    private fun addDestinationMarker(lat: Double, lng: Double) {
        if (destinationMarker != null) {
            map.removeMapObject(destinationMarker!!)
        }
        endPoint = LatLng(lat, lng)
        destinationMarker = MapMarker()
        destinationMarker?.coordinate = GeoCoordinate(lat, lng)
        map.addMapObject(destinationMarker!!)
        map.setCenter(GeoCoordinate(lat, lng), Map.Animation.NONE)
    }

    //show dialog find the way
    private fun showDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog)
        val window: Window = dialog.window ?: return
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val windowAttributes: WindowManager.LayoutParams = window.attributes
        windowAttributes.gravity = Gravity.CENTER
        window.attributes = windowAttributes

        val tvStart: TextView = dialog.findViewById(R.id.dialog_start)
        val tvEnd: TextView = dialog.findViewById(R.id.dialog_end)
        val ivChange: ImageView = dialog.findViewById(R.id.dialog_ivChange)
        val btnCancel: Button = dialog.findViewById(R.id.dialog_btnCancel)
        val btnOk: Button = dialog.findViewById(R.id.dialog_btnOk)
        val spinner: Spinner = dialog.findViewById(R.id.spin)
        //Spinner
        val list: MutableList<String> = java.util.ArrayList()
        list.add("Car")
        list.add("Bicycle")
        list.add("Pedestrian")
        val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
        spinner.adapter = arrayAdapter
        spinner.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View, position: Int, id: Long
            ) {
                Log.e("spinner", "${list[position]}")
                transport = "${list[position]}"
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }
        tvStart.text = "$startPoint\n $addressStart"
        tvEnd.text = "$endPoint\n $addressDestination"

        ivChange.setOnClickListener {
            val mid = startPoint
            startPoint = endPoint
            endPoint = mid
            var string = tvStart.text.toString()
            tvStart.text = tvEnd.text
            tvEnd.text = string
            string = addressDestination
            addressDestination = addressStart
            addressStart = string
            tvStart.text = "$startPoint\n $addressStart"
            tvEnd.text = "$endPoint\n $addressDestination"
            addMyLocationMarker(startPoint!!.latitude, startPoint!!.longitude)
            addDestinationMarker(endPoint!!.latitude, endPoint!!.longitude)
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnOk.setOnClickListener {
            cleanWay()
            createRoute(0)
            createRoute(1)
            dialog.dismiss()
        }
        dialog.show()
    }

    //Draw the way
    private fun createRoute(count: Int) {
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
        if (transport == "Car") {
            routeOptions.transportMode = RouteOptions.TransportMode.CAR
        } else {
            if (transport == "Bicycle") {
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
                        map.addMapObject(mapRoute!!)
                        mapRouteList.add(mapRoute!!)
                        map.setCenter(
                            GeoCoordinate(startPoint!!.latitude, startPoint!!.longitude),
                            Map.Animation.NONE
                        )
                        map.zoomLevel = map.maxZoomLevel / 1.4f
                        var inforOfWay = ""
                        inforOfWay += "Time: ${formatTime(route.getTtaExcludingTraffic(Route.WHOLE_ROUTE)!!.duration)}\n"
                        inforOfWay += "Distance: ${mapRoute!!.route!!.length / 1000.0}km\n"
                        if (count == 1) {
                            way_fastest.text = inforOfWay
                        } else {
                            way_other.text = inforOfWay
                        }
                        Log.e("infor", "$inforOfWay")
                    } else {
                        Log.e("infor", "onCalculateRouteFinished: $routingError")
                        Toast.makeText(baseContext, "Can't find the way", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        tvTransport.text = "Transport: $transport"
        scrollView.visibility = View.VISIBLE
    }

    private fun cleanWay() {    //remove old route
        if (mapRouteList.isNotEmpty()) {
            map.removeMapObjects(mapRouteList)
            mapRouteList.clear()
        }
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