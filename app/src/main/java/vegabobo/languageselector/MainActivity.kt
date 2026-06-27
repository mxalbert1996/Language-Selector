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
import vegabobo.languageselector.sync.AppLanguageSyncCoordinator
import vegabobo.languageselector.ui.screen.Navigation
import vegabobo.languageselector.ui.theme.LanguageSelector

@AndroidEntryPoint
class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    private val acRequestCode = 1

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val requestPermissionResultListener = this::onRequestPermissionResult

    private fun bindShizuku() {
        log("Preferred Shizuku backend available")
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            log("Shizuku permission granted")
            AppLanguageSyncCoordinator(applicationContext).syncImmediate()
        }
    }

    private fun checkPermission(code: Int): Boolean =
        if (Shizuku.checkSelfPermission() ==
            PackageManager.PERMISSION_GRANTED
        ) {
            bindShizuku()
            true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            false
        } else {
            Shizuku.requestPermission(code)
            false
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
            requestNotificationPermission()
        }

        if (Shizuku.pingBinder() && savedInstanceState == null) {
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            checkPermission(acRequestCode)
        }
        AppLanguageSyncCoordinator(applicationContext).syncImmediate()
    }

    override fun onResume() {
        super.onResume()
        if (Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        ) {
            log("Shizuku available")
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        super.onDestroy()
    }
}
