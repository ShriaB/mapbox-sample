package com.example.mapboxsample

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.common.location.Location
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

/**
 * Main Activity controls the MapView
 * This app is able to render users live location
 * And it can also handle gestures like Map click or move
 */

class MainActivity : AppCompatActivity(), PermissionsListener {

    private lateinit var mapView: MapView
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var origin: Point
    private lateinit var destinationPosition: Point
    private lateinit var startButton: Button
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private val navigationLocationProvider = NavigationLocationProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startButton = findViewById(R.id.btn_start_nav)
        mapView = findViewById(R.id.map_view)
        onMapReady()
        startButton.setOnClickListener{
            mapView.location.enabled=true
        }
    }

    private fun onMapReady() {
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(14.0)
                .build()
        )
        mapView.getMapboxMap().loadStyleUri(
            Style.MAPBOX_STREETS
        ) {
            // Request for location permission
            enableLocation()
            // Create an instance of the Annotation API and get the PointAnnotationManager.
            pointAnnotationManager = mapView?.annotations!!.createPointAnnotationManager()
        }
    }

    private fun setupGesturesListener() {
        mapView.apply{
            gestures.addOnMoveListener(onMoveListener)

            // When any point on map is clicked it is annotated
            gestures.addOnMapClickListener{point->
                Toast.makeText(context, "Lat:${point.latitude()}, long:${point.longitude()}", Toast.LENGTH_LONG).show()
                destinationPosition = Point.fromLngLat(point.longitude(), point.latitude())

                // Set options for the resulting symbol layer.
                val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                    // Define a geographic coordinate.
                    .withPoint(destinationPosition)
                    // Specify the bitmap you assigned to the point annotation
                    // The bitmap will be added to map style automatically.
                    .withIconImage(AppCompatResources.getDrawable(this@MainActivity, R.drawable.baseline_location_on_24)!!.toBitmap())

                // Deleting the previous annotation
                pointAnnotationManager.delete(pointAnnotationManager.annotations)

                // Add the resulting pointAnnotation to the map.
                pointAnnotationManager?.create(pointAnnotationOptions)
                true
            }
        }
    }


    private fun initLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D(
                topImage = AppCompatResources.getDrawable(
                this@MainActivity,
                R.drawable.baseline_my_location_24,
            ),
                scaleExpression = interpolate {
                    linear()
                    zoom()
                    stop {
                        literal(0.0)
                        literal(0.6)
                    }
                    stop {
                        literal(20.0)
                        literal(1.0)
                    }
                }.toJson())
        }
        locationComponentPlugin.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        locationComponentPlugin.addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
    }

    /**
     * Gives Error for granting permission
     * Hence commented
      */
/**
    private val locationObserver = object : LocationObserver {
        /**
         * Invoked as soon as the [Location] is available.
         */
        override fun onNewRawLocation(rawLocation: android.location.Location) {
        // Not implemented in this example. However, if you want you can also
        // use this callback to get location updates, but as the name suggests
        // these are raw location updates which are usually noisy.
        }

        /**
         * Provides the best possible location update, snapped to the route or
         * map-matched to the road if possible.
         */
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(
                enhancedLocation,
                locationMatcherResult.keyPoints,
            )
            // Invoke this method to move the camera to your current location.
            updateCamera(enhancedLocation)
        }
    }


    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerLocationObserver(locationObserver)
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterLocationObserver(locationObserver)
            }
        },
        onInitialize = this::initNavigation
    )

    private fun initNavigation() {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this)
                .accessToken(getString(R.string.mapbox_access_token))
                .build()
        )
// Instantiate the location component which is the key component to fetch location updates.
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }
    }


    private fun updateCamera(location: android.location.Location){
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(location.longitude, location.latitude))
                .zoom(14.0)
                .build()
        )
    }

    */

    /**
     * Requesting Location Permissions
     * Using PermissionsManager
     */
    private fun enableLocation(){
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            onMapReady()
            initLocationComponent()
//            initNavigation()
            setupGesturesListener()
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        TODO("Not yet implemented")
    }

    override fun onPermissionResult(granted: Boolean) {
        if(granted){
            enableLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    /**
     * LocationComponentPlugin listeners
     */
    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
        mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)
    }

    /**
     * Gesture Listener on map
     */
    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            onCameraTrackingDismissed()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }

    private fun onCameraTrackingDismissed() {
//        Toast.makeText(this, "onCameraTrackingDismissed", Toast.LENGTH_SHORT).show()
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }
}