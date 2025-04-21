// MainActivity.kt
package com.example.taller2

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

data class LongClickMarker(val position: LatLng, val title: String)

fun ambientLightSensorFlow(context: Context): Flow<Float> = callbackFlow {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
    val sensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
    val listener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            event?.let { trySend(it.values.first()) }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }
    sensorManager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
    awaitClose { sensorManager.unregisterListener(listener) }
}

fun simulatedLocationFlow(): Flow<Location> = flow {
    val location = Location("simulated").apply {
        latitude = 4.60971
        longitude = -74.08175
    }
    while (true) {
        emit(location)
        delay(2000)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf("main") }
                when (currentScreen) {
                    "main" -> MainScreen(
                        onCameraClick = { currentScreen = "media" },
                        onMapClick = { currentScreen = "map" }
                    )
                    "media" -> MediaPickerScreen(onBack = { currentScreen = "main" })
                    "map" -> MapScreen(onBack = { currentScreen = "main" })
                }
            }
        }
    }
}

@Composable
fun MainScreen(onCameraClick: () -> Unit, onMapClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = onCameraClick, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.camara),
                    contentDescription = "Icono de cámara",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir Cámara")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onMapClick, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.mapa),
                    contentDescription = "Icono de mapa",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir Mapa")
            }
        }
    }
}

@Composable
fun MediaPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var isPhoto by remember { mutableStateOf(true) }
    val tempMediaUri = remember { mutableStateOf<Uri?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedMediaUri = tempMediaUri.value
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            selectedMediaUri = tempMediaUri.value
        }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedMediaUri = uri
    }

    fun createTempFileUri(context: Context, extension: String): Uri {
        val file = File.createTempFile("temp", extension, context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    Column(modifier = Modifier.fillMaxSize().padding(30.dp)) {
        Button(onClick = onBack) { Text("Volver") }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Foto")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = !isPhoto, onCheckedChange = { isChecked ->
                isPhoto = !isChecked
                selectedMediaUri = null
                tempMediaUri.value = null
            })
            Spacer(modifier = Modifier.width(8.dp))
            Text("Video")
        }
        Spacer(modifier = Modifier.height(16.dp))

        selectedMediaUri?.let { uri ->
            if (isPhoto) {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(300.dp).align(Alignment.CenterHorizontally),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                key(uri) {
                    AndroidView(factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoURI(uri)
                            setOnPreparedListener { it.isLooping = true; start() }
                        }
                    }, modifier = Modifier.size(300.dp).align(Alignment.CenterHorizontally))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        val newUri = createTempFileUri(context, if (isPhoto) ".jpg" else ".mp4")
                        tempMediaUri.value = newUri
                        selectedMediaUri = null
                        if (isPhoto) takePictureLauncher.launch(newUri)
                        else captureVideoLauncher.launch(newUri)
                    }
                },
                modifier = Modifier.weight(2f).padding(end = 6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(if (isPhoto) "Tomar Foto" else "Grabar Video",
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(fontSize = 14.sp))
            }

            Button(
                onClick = {
                    pickMediaLauncher.launch(if (isPhoto) "image/*" else "video/*")
                },
                modifier = Modifier.weight(2f).padding(start = 6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(if (isPhoto) "Seleccionar Foto" else "Seleccionar Video",
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(fontSize = 14.sp))
            }
        }

    }
}

@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var currentLocation by remember { mutableStateOf(LatLng(4.60971, -74.08175)) }
    val routePoints = remember { mutableStateListOf<LatLng>() }
    var searchQuery by remember { mutableStateOf("") }
    var searchedLocation by remember { mutableStateOf<LatLng?>(null) }
    val longClickMarkers = remember { mutableStateListOf<LongClickMarker>() }
    val coroutineScope = rememberCoroutineScope()
    var trackingUser by remember { mutableStateOf(false) }

    val ambientLight = ambientLightSensorFlow(context).collectAsState(initial = 100f).value
    val mapStyleJson = if (ambientLight < 10f)
        """[{"elementType": "geometry", "stylers": [{"saturation": -100}, {"lightness": -30}]}]"""
    else "[]"

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }

    LaunchedEffect(Unit) {
        simulatedLocationFlow().collect { location ->
            val newLatLng = LatLng(location.latitude, location.longitude)
            currentLocation = newLatLng
            routePoints.add(newLatLng)
            if (trackingUser) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(newLatLng, 16f))
            }
        }
    }

    fun searchLocationByText(query: String) {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocationName(query, 1)
        if (addresses?.isNotEmpty() == true) {
            val address = addresses[0]
            val latLng = LatLng(address.latitude, address.longitude)
            searchedLocation = latLng
            trackingUser = false
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        } else {
            Toast.makeText(context, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapStyleOptions = MapStyleOptions(mapStyleJson)),
                onMapLongClick = { clickedLatLng ->
                    coroutineScope.launch {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = try {
                            geocoder.getFromLocation(clickedLatLng.latitude, clickedLatLng.longitude, 1)
                        } catch (e: Exception) { emptyList() }
                        val title = if (addresses?.isNotEmpty() == true)
                            addresses[0].getAddressLine(0) else "Dirección no encontrada"
                        longClickMarkers.add(LongClickMarker(clickedLatLng, title))
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(clickedLatLng, 16f))
                    }
                }
            ) {
                Marker(state = MarkerState(position = currentLocation), title = "Estás aquí")
                if (routePoints.size > 1) Polyline(points = routePoints.toList())
                searchedLocation?.let {
                    Marker(state = MarkerState(position = it), title = "Resultado búsqueda")
                }
                longClickMarkers.forEach { marker ->
                    Marker(state = MarkerState(position = marker.position), title = marker.title)
                }
            }

            Column(
                modifier = Modifier
                    .padding(top = 60.dp, start = 8.dp, end = 8.dp)
                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                    .padding(8.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ubicación actual")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = trackingUser,
                        onCheckedChange = {
                            trackingUser = it
                            if (it) {
                                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f))
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar dirección") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, shape = RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { searchLocationByText(searchQuery) })
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart
            ) {
                Button(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Text("Volver")
                }
            }
        }
    }
}
