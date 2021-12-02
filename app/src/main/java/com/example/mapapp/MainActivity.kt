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
import androidx.lifecycle.ViewModelProviders
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.mapping.*
import kotlinx.android.synthetic.main.activity_main.*

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_LOCATION_PERMISSION = 1
        const val latHN: Double = 21.03549853898585
        const val lngHN: Double = 105.83270413801074
    }

    lateinit var mMainViewModel: MapViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mMainViewModel = ViewModelProviders.of(this).get(MapViewModel::class.java)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissions()
        checkConnectivity()
        setUpViewModel()
        setUpView()
        setUpMap()
    }

    private fun setUpViewModel() {
        mMainViewModel.isShowAddress.observe(this, {
            if (it)
                map_llAddress.visibility = View.VISIBLE
            else
                map_llAddress.visibility = View.GONE
        })
        mMainViewModel.isShowWay.observe(this, {
            if (it)
                scrollView.visibility = View.VISIBLE
            else
                scrollView.visibility = View.GONE
        })
        mMainViewModel.isShowSearch.observe(this, {
            if (it)
                map_llSearch.visibility = View.VISIBLE
            else
                map_llSearch.visibility = View.GONE
        })
        mMainViewModel.transport.observe(this, {
            when (it) {
                0 -> tvTransport.text = "Transport: Car"
                1 -> tvTransport.text = "Transport: Bicycle"
                2 -> tvTransport.text = "Transport: Pedestrian"
            }
        })
        mMainViewModel.resultAddress.observe(this, {
            map_tvAddress.text = it
        })
        mMainViewModel.resultWayShort.observe(this, {
            way_shortest.text = it
        })
        mMainViewModel.resultOtherWay.observe(this, {
            way_other.text = it
        })
    }

    private fun setUpView() {
        map_ivNavigation.setOnClickListener {
            map_llAddress.visibility = View.GONE
            map_llSearch.visibility = View.GONE
            scrollView.visibility = View.GONE
            if (mMainViewModel.getAddressStart() == null) {
                Toast.makeText(baseContext, "Please get current position", Toast.LENGTH_SHORT)
                    .show()
            } else {
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
                    mMainViewModel.cleanWay()
                    mMainViewModel.searchLocation(map_etSearch.text.toString())
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
        initMap(mapFragment)
    }

    private fun initMap(mapFragment: AndroidXMapFragment) {
        mapFragment.init { error ->
            if (error == OnEngineInitListener.Error.NONE) {
                var map = mapFragment.map!!
                mMainViewModel.createMap(map)
                mapFragment.mapGesture!!.addOnGestureListener(object :
                    MapGesture.OnGestureListener.OnGestureListenerAdapter() {
                    override fun onTapEvent(p: PointF): Boolean {
                        mMainViewModel.onPressOnMap(p)
                        return false
                    }

                    override fun onLongPressEvent(p: PointF): Boolean {
                        mMainViewModel.onPressOnMap(p)
                        return false
                    }
                }, 3, true)
            } else {
                Log.e("map-error", "init error")
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
                    mMainViewModel.setAddressStart("lat/lng: (${location.latitude},${location.longitude})\nYour position")
                    mMainViewModel.addMyLocationMarker(location.latitude, location.longitude)
                } else {
                    Log.e("HEREmap", "My location null")
                }
            }
    }

    //Draw the way
    private fun createRoute(count: Int) {
        if (count == 1) {
            mMainViewModel.createRoute(count, this)
        } else {
            mMainViewModel.createRoute(count, this)
        }
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
                mMainViewModel.transport.value = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                mMainViewModel.transport.value = 0
            }
        }
        tvStart.text = "${mMainViewModel.getAddressStart()}"
        tvEnd.text = "${mMainViewModel.getAddressDestination()}"

        ivChange.setOnClickListener {   //change 2 position
            mMainViewModel.reversePosition()
            tvStart.text = "${mMainViewModel.getAddressStart()}"
            tvEnd.text = "${mMainViewModel.getAddressDestination()}"
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnOk.setOnClickListener {
            mMainViewModel.cleanWay()
            createRoute(0)
            createRoute(1)
            dialog.dismiss()
        }
        dialog.show()
    }

}