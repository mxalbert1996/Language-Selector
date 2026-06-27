package vegabobo.languageselector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import rikka.shizuku.Shizuku
import vegabobo.languageselector.service.PreferredPrivilegedBackend
import vegabobo.languageselector.service.PrivilegedServiceManager
import vegabobo.languageselector.sync.AppLanguageSyncCoordinator
import vegabobo.languageselector.ui.screen.Navigation
import vegabobo.languageselector.ui.theme.LanguageSelector

@AndroidEntryPoint
class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    private val acRequestCode = 1

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val requestPermissionResultListener = this::onRequestPermissionResult

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            log("Shizuku permission granted")
            AppLanguageSyncCoordinator(applicationContext).syncImmediate()
        }
        requestNotificationPermission()
    }

    private fun checkPermission(code: Int) {
        val status = PrivilegedServiceManager.getBackendStatus()
        if (status.rootAvailable) {
            log("ROOT available")
        } else if (status.shizukuPermissionGranted) {
            log("Shizuku backend available")
        } else if (status.shouldRequestShizukuPermission) {
            if (status.shouldShowShizukuRationale) {
                log("Shizuku shouldShowRequestPermissionRationale")
            }
            Shizuku.requestPermission(code)
            return
        } else {
            log("No backend available")
        }
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LanguageSelector { Navigation() }
        }

        if (savedInstanceState == null) {
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            checkPermission(acRequestCode)
        }
        AppLanguageSyncCoordinator(applicationContext).syncImmediate()
    }

    override fun onResume() {
        super.onResume()
        when (PrivilegedServiceManager.getBackendStatus().preferredBackend) {
            PreferredPrivilegedBackend.ROOT -> log("Root available")
            PreferredPrivilegedBackend.SHIZUKU -> log("Shizuku available")
            PreferredPrivilegedBackend.NONE -> Unit
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        super.onDestroy()
    }
}
