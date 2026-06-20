package vegabobo.languageselector

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.topjohnwu.superuser.ipc.RootService
import dagger.hilt.android.AndroidEntryPoint
import rikka.shizuku.Shizuku
import vegabobo.languageselector.service.RootUserService
import vegabobo.languageselector.service.UserService
import vegabobo.languageselector.service.UserServiceProvider
import vegabobo.languageselector.sync.AppLanguageSyncCoordinator
import vegabobo.languageselector.ui.screen.Navigation
import vegabobo.languageselector.ui.screen.main.OperationMode
import vegabobo.languageselector.ui.theme.LanguageSelector

object ShizukuArgs {
    val userServiceArgs: Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    private val acRequestCode = 1
    private var onConnectedListener: (() -> Unit)? = null

    private fun bindShizuku() {
        Shizuku.bindUserService(ShizukuArgs.userServiceArgs, UserServiceProvider.connection)
    }

    private fun triggerImmediateSync() {
        UserServiceProvider.connection.runImmediateSyncOnce {
            AppLanguageSyncCoordinator(applicationContext).syncImmediate()
        }
    }

    private val REQUEST_PERMISSION_RESULT_LISTENER = this::onRequestPermissionResult

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            bindShizuku()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LanguageSelector { Navigation() }
        }

        if (Shizuku.pingBinder() && savedInstanceState == null) {
            Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
            checkPermission(acRequestCode)
        }

        RootReceivedListener.setListener(
            object : IRootListener {
                override fun onRootReceived() {
                    val intent = Intent(application, RootUserService::class.java)
                    runOnUiThread {
                        RootService.bind(intent, UserServiceProvider.connection)
                    }
                }
            },
        )

        onConnectedListener = {
            triggerImmediateSync()
        }
        UserServiceProvider.addOnConnectedListener(onConnectedListener!!)
    }

    override fun onResume() {
        super.onResume()
        if (
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
            !UserServiceProvider.isConnected()
        ) {
            bindShizuku()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
        onConnectedListener?.let { UserServiceProvider.connection.removeOnConnectedListener(it) }
        RootReceivedListener.destroy()
        if (UserServiceProvider.isConnected()) {
            when (UserServiceProvider.opMode) {
                OperationMode.ROOT -> RootService.unbind(UserServiceProvider.connection)
                OperationMode.SHIZUKU -> Shizuku.unbindUserService(
                    ShizukuArgs.userServiceArgs,
                    UserServiceProvider.connection,
                    true,
                )
                else -> Log.d(BuildConfig.APPLICATION_ID, "UserService not bound.")
            }
        }
        super.onDestroy()
    }
}
