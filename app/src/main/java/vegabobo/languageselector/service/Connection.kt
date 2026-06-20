package vegabobo.languageselector.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import vegabobo.languageselector.IUserService

class Connection : ServiceConnection {

    var SERVICE: IUserService? = null
    private val onConnectedListeners = mutableListOf<() -> Unit>()
    private var immediateSyncTriggered = false

    fun addOnConnectedListener(listener: () -> Unit) {
        onConnectedListeners.add(listener)
        SERVICE?.let { listener() }
    }

    fun removeOnConnectedListener(listener: () -> Unit) {
        onConnectedListeners.remove(listener)
    }

    fun runImmediateSyncOnce(block: () -> Unit) {
        if (immediateSyncTriggered || SERVICE == null) return
        immediateSyncTriggered = true
        block()
    }

    fun set(service: IUserService?) {
        if (SERVICE == null) {
            SERVICE = service
            if (service != null) {
                onConnectedListeners.forEach { it() }
            }
        }
    }

    fun hasService(): Boolean = SERVICE != null

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        set(IUserService.Stub.asInterface(service))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        SERVICE = null
        immediateSyncTriggered = false
    }
}
