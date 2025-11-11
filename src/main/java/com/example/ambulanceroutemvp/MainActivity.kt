package com.example.ambulanceroutemvp

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.opencsv.CSVReader
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.InputStreamReader
import kotlin.math.*
import okhttp3.OkHttpClient
import okhttp3.Request

data class Hospital(
    val name: String,
    val lat: Double,
    val lon: Double
)


class MainActivity : ComponentActivity() {

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fused = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MaterialTheme {
                val context = LocalContext.current

                // Load OpenStreetMap configuration
                Configuration.getInstance().load(
                    context,
                    context.getSharedPreferences("osmdroid", MODE_PRIVATE)
                )

                // Load hospitals from CSV
                val hospitals by remember { mutableStateOf(loadHospitalsFromCsv(context)) }
                var myLocation by remember { mutableStateOf<GeoPoint?>(null) }
                var nearest by remember { mutableStateOf<Hospital?>(null) }

                // Permission launcher
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        fused.lastLocation.addOnSuccessListener { loc ->
                            loc?.let { myLocation = GeoPoint(it.latitude, it.longitude) }
                        }
                    }
                }

                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) {
                            fused.lastLocation.addOnSuccessListener { loc ->
                                loc?.let { myLocation = GeoPoint(it.latitude, it.longitude) }
                            }
                        }
                    }

                // Request or fetch location
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        fused.lastLocation.addOnSuccessListener { loc ->
                            loc?.let { myLocation = GeoPoint(it.latitude, it.longitude) }
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            myLocation?.let { loc ->
                                nearest = hospitals.minByOrNull {
                                    distanceKm(it.lat, it.lon, loc.latitude, loc.longitude)
                                }
                            }
                        }) {
                            Text("Nearest")
                        }
                    }
                ) { inner ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(inner)
                    ) {
                        val mapView = remember { MapView(context) }

                        // Map View
                        Box(Modifier.weight(1f)) {
                            AndroidView(
                                factory = {
                                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                                    mapView.setMultiTouchControls(true)
                                    mapView.controller.setZoom(12.5)
                                    myLocation?.let { mapView.controller.setCenter(it) }
                                    mapView
                                },
                                update = { map ->
                                    map.overlays.clear()

                                    // 🚑 Ambulance marker
                                    myLocation?.let { loc ->
                                        val marker = Marker(map)
                                        marker.position = loc
                                        marker.title = "Ambulance (You)"
                                        map.overlays.add(marker)
                                        map.controller.setCenter(loc)
                                    }

                                    // Hospital markers
                                    hospitals.forEach { h ->
                                        val m = Marker(map)
                                        m.position = GeoPoint(h.lat, h.lon)
                                        m.title = "${h.name}\n)"
                                        map.overlays.add(m)
                                    }

                                    // Real route via OpenRouteService
                                    if (nearest != null && myLocation != null) {
                                        drawRealRoute(
                                            myLocation!!,
                                            GeoPoint(nearest!!.lat, nearest!!.lon),
                                            map,
                                            "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImIzNDM1ZWYyYTEzNTQ5ZmQ5M2VkMGE2YjYzZGY3NjVkIiwiaCI6Im11cm11cjY0In0=" // <--- Replace with your ORS key
                                        )
                                    }

                                    map.invalidate()
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (myLocation == null) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Fetching location…")
                                }
                            }
                        }

                        Divider()

                        Text(
                            "Nearby Hospitals (Delhi)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(8.dp)
                        )

                        LazyColumn(Modifier.height(180.dp)) {
                            items(hospitals) { h ->
                                Card(
                                    onClick = { nearest = h },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(h.name)
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

    //Load hospital data from CSV
    private fun loadHospitalsFromCsv(context: android.content.Context): List<Hospital> {
        val list = mutableListOf<Hospital>()
        try {
            val input = context.assets.open("Delhi_Hospitals_Data.csv")
            val reader = CSVReader(InputStreamReader(input))
            val lines = reader.readAll().drop(1) // skip header

            for (line in lines) {
                if (line.size >= 3) {
                    val name = line[0]
                    val lat = line[1].toDoubleOrNull() ?: continue
                    val lon = line[2].toDoubleOrNull() ?: continue

                    list.add(Hospital(name, lat, lon))
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }


    // Draw realistic route using OpenRouteService API
    private fun drawRealRoute(
        start: GeoPoint,
        end: GeoPoint,
        mapView: MapView,
        apiKey: String
    ) {
        val url =
            "https://api.openrouteservice.org/v2/directions/driving-car?api_key=$apiKey&start=${start.longitude},${start.latitude}&end=${end.longitude},${end.latitude}"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                val json = response.body?.string()
                if (json != null) {
                    val jsonObj = JSONObject(json)
                    val coords = jsonObj
                        .getJSONArray("features")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates")

                    val routePoints = mutableListOf<GeoPoint>()
                    for (i in 0 until coords.length()) {
                        val coord = coords.getJSONArray(i)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        routePoints.add(GeoPoint(lat, lon))
                    }

                    runOnUiThread {
                        val line = Polyline()
                        line.setPoints(routePoints)
                        line.outlinePaint.strokeWidth = 8f
                        mapView.overlays.add(line)
                        mapView.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
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
