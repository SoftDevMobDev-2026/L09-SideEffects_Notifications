package com.example.l09_sideeffects_notifications

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ComposeEffectsApp() } }
    }
}

enum class Demo { MENU, COROUTINE, SIDE_EFFECTS, NOTIFICATION }

@Composable
fun ComposeEffectsApp() {
    var demo by remember { mutableStateOf(Demo.MENU) }
    Surface(Modifier.fillMaxSize()) {
        when (demo) {
            Demo.MENU -> DemoMenu { demo = it }
            Demo.COROUTINE -> DemoPage("1. Main Thread and Coroutine", { demo = Demo.MENU }) { CoroutineDemo() }
            Demo.SIDE_EFFECTS -> DemoPage("2. Compose Side Effects", { demo = Demo.MENU }) { SideEffectsDemo() }
            Demo.NOTIFICATION -> DemoPage("3. Local Notification", { demo = Demo.MENU }) { NotificationDemo() }
        }
    }
}

@Composable
fun DemoMenu(onOpen: (Demo) -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Seminar 9: Compose Effects", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { onOpen(Demo.COROUTINE) }) { Text("1. Main Thread + Coroutine") }
        Button(onClick = { onOpen(Demo.SIDE_EFFECTS) }) { Text("2. Compose Side Effects") }
        Button(onClick = { onOpen(Demo.NOTIFICATION) }) { Text("3. Local Notification") }
    }
}

@Composable
fun DemoPage(title: String, back: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = back) { Text("← Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun CoroutineDemo() {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Ready") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message)
        Button(onClick = {
            scope.launch {
                message = "Working..."
                withContext(Dispatchers.IO) { Thread.sleep(2000) } // teaching simulation only
                message = "Finished without blocking the UI"
            }
        }) { Text("Run background work") }
        Text("Thread.sleep only simulates slow work here. Do not use it for real asynchronous work.")
    }
}

@Composable
fun SideEffectsDemo() {
    var key by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Press Restart Effect") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(key) {
        if (key > 0) {
            message = "LaunchedEffect started..."
            delay(1000)
            message = "LaunchedEffect finished for key = $key"
        }
    }

    SideEffect {
        Log.d("Seminar9", "Successful composition; key = $key")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Log.d("Seminar9", "Lifecycle event: $event")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Log.d("Seminar9", "Observer removed")
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message)
            Button(onClick = { key++ }) { Text("Restart LaunchedEffect") }
            Button(onClick = {
                scope.launch { snackbarHostState.showSnackbar("Coroutine launched from a click") }
            }) { Text("Show Snackbar") }
            Text("Check Logcat for SideEffect and DisposableEffect messages.")
        }
    }
}

private const val DEMO_CHANNEL_ID = "seminar9_demo"

fun createDemoNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= 33) {
        val channel = NotificationChannel(
            DEMO_CHANNEL_ID,
            "Seminar 9 Demo",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifications from Seminar 9"}

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}

fun showDemoNotification(context: Context) {
    val notification = NotificationCompat.Builder(context, DEMO_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_dialog_info)
        .setContentTitle("Seminar 9 Notification")
        .setContentText("Notification from our Compose demo.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    if (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(1001, notification)
    }
}

@Composable
fun NotificationDemo() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Ready") }

    LaunchedEffect(Unit) {
        createDemoNotificationChannel(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showDemoNotification(context)
            status = "Permission granted — notification sent"
        } else {
            status = "Notification permission was not granted"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("A standalone local notification example for Seminar 9.")
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                showDemoNotification(context)
                status = "Notification sent"
            }
        }) { Text("Show Notification") }
        Text(status)
    }
}
