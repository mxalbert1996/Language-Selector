package vegabobo.languageselector.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import vegabobo.languageselector.IUserService

class Connection : ServiceConnection {

    private var service: IUserService? = null

    fun serviceOrNull(): IUserService? = service

    private fun set(service: IUserService?) {
        this.service = service
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        set(IUserService.Stub.asInterface(service))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }
}
