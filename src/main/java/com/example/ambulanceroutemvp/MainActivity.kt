package com.example.ambulanceroutemvp
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.*

data class Hospital(val name: String, val lat: Double, val lon: Double)

class MainActivity : ComponentActivity() {

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialize location client
        val fused = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MaterialTheme {
                val context = LocalContext.current

                // Load OpenStreetMap configuration
                Configuration.getInstance().load(
                    context,
                    context.getSharedPreferences("osmdroid", MODE_PRIVATE)
                )

                var myLocation by remember { mutableStateOf<GeoPoint?>(null) }
                var nearest by remember { mutableStateOf<Hospital?>(null) }

                val hospitals = remember {
                    listOf(
                        Hospital("SCI International Hospital", 28.55032, 77.2341),
                        Hospital("Netrayatan Hospita", 28.5302, 77.2454),
                        Hospital("Apollo Spectra Hospitals", 28.5460, 77.2480),
                        Hospital("Fortis C-Doc Hospital", 28.5474, 77.2496)
                    )
                }

                val permissionLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) {
                            @SuppressLint("MissingPermission")
                            fun startLocationUpdates() {
                                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 2000L
                                ).build()

                                val locationCallback = object : LocationCallback() {
                                    override fun onLocationResult(result: LocationResult) {
                                        result.lastLocation?.let { loc ->
                                            myLocation = GeoPoint(loc.latitude, loc.longitude)
                                        }
                                    }
                                }

                                fused.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
                            }

                        }
                    }

                // request or fetch location
                LaunchedEffect(Unit) {
                    when (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )) {
                        PackageManager.PERMISSION_GRANTED -> {
                            fused.lastLocation.addOnSuccessListener { loc ->
                                loc?.let { myLocation = GeoPoint(it.latitude, it.longitude) }
                            }
                        }

                        else -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            myLocation?.let { loc ->
                                nearest = hospitals.minByOrNull {
                                    distanceKm(
                                        it.lat,
                                        it.lon,
                                        loc.latitude,
                                        loc.longitude
                                    )
                                }
                            }
                        }) {
                            Text("Nearest")
                        }
                    }
                ) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val mapView = remember { MapView(context) }

                        // Map UI
                        Box(Modifier.weight(1f)) {
                            AndroidView(
                                factory = {
                                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                                    mapView.setMultiTouchControls(true)
                                    mapView.controller.setZoom(13.0)
                                    myLocation?.let {
                                        mapView.controller.setCenter(it)
                                    }
                                    mapView
                                },
                                update = { map ->
                                    map.overlays.clear()

                                    // Ambulance marker
                                    myLocation?.let { loc ->
                                        val marker = Marker(map)
                                        marker.position = loc
                                        marker.title = "Ambulance"
                                        map.overlays.add(marker)
                                    }

                                    // Hospital markers
                                    hospitals.forEach { h ->
                                        val marker = Marker(map)
                                        marker.position = GeoPoint(h.lat, h.lon)
                                        marker.title = h.name
                                        map.overlays.add(marker)
                                    }

                                    // Route polyline
                                    if (nearest != null && myLocation != null) {
                                        val route = Polyline()
                                        route.setPoints(
                                            listOf(
                                                myLocation!!,
                                                GeoPoint(nearest!!.lat, nearest!!.lon)
                                            )
                                        )
                                        map.overlays.add(route)
                                    }

                                    map.invalidate()
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (myLocation == null) {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Fetching location…")
                                }
                            }
                        }

                        Divider()

                        Text(
                            "Nearby Hospitals",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(8.dp)
                        )

                        LazyColumn(Modifier.height(180.dp)) {
                            items(hospitals) { hospital ->
                                Card(
                                    onClick = { nearest = hospital },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(hospital.name)
                                    }
                                }
                            }
                        }

                        nearest?.let {
                            Text(
                                "Nearest Hospital: ${it.name}",
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
