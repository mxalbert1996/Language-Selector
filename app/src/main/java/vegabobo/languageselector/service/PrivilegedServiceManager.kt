package vegabobo.languageselector.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import vegabobo.languageselector.BuildConfig
import vegabobo.languageselector.IUserService
import vegabobo.languageselector.log

enum class PrivilegedAcquisitionPolicy { AUTO, ROOT_ONLY, SHIZUKU_ONLY }

enum class PreferredPrivilegedBackend {
    NONE,
    SHIZUKU,
    ROOT,
}

data class PrivilegedBackendStatus(
    val rootAvailable: Boolean,
    val shizukuReachable: Boolean,
    val shizukuPermissionGranted: Boolean,
    val shouldShowShizukuRationale: Boolean,
) {
    val shizukuUsable: Boolean
        get() = shizukuReachable && shizukuPermissionGranted

    val preferredBackend: PreferredPrivilegedBackend
        get() = when {
            rootAvailable -> PreferredPrivilegedBackend.ROOT
            shizukuUsable -> PreferredPrivilegedBackend.SHIZUKU
            else -> PreferredPrivilegedBackend.NONE
        }

    val shouldRequestShizukuPermission: Boolean
        get() = !rootAvailable &&
            shizukuReachable &&
            !shizukuPermissionGranted &&
            !shouldShowShizukuRationale
}

class PrivilegedServiceLease internal constructor(
    val service: IUserService,
    private val leaseToken: LeaseToken,
    private val manager: PrivilegedServiceManager,
) {
    @Volatile
    private var released = false

    suspend fun release() {
        if (released) return
        released = true
        manager.releaseLease(leaseToken)
    }
}

data class LeaseToken(val generation: Long, val id: Long)

sealed class PrivilegedAcquisitionResult {
    data class Acquired(val lease: PrivilegedServiceLease) : PrivilegedAcquisitionResult()
    data object NoPrivilege : PrivilegedAcquisitionResult()
    data class TransientFailure(val error: Throwable) : PrivilegedAcquisitionResult()
}

object PrivilegedServiceManager {
    private val mutex = Mutex()
    private var connection: Connection? = null
    private var activeBackend: PrivilegedBackend? = null
    private var activeLeases = 0
    private var generation = 0L
    private var nextLeaseId = 1L
    private val leasedIdsByGeneration = mutableMapOf<Long, MutableSet<Long>>()

    private enum class PrivilegedBackend { ROOT, SHIZUKU }

    fun getBackendStatus(): PrivilegedBackendStatus {
        val rootAvailable = tryRootAvailable()
        val shizukuReachable = Shizuku.pingBinder()
        val shizukuPermissionGranted = shizukuReachable &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        val shouldShowShizukuRationale = shizukuReachable && !shizukuPermissionGranted &&
            Shizuku.shouldShowRequestPermissionRationale()
        return PrivilegedBackendStatus(
            rootAvailable = rootAvailable,
            shizukuReachable = shizukuReachable,
            shizukuPermissionGranted = shizukuPermissionGranted,
            shouldShowShizukuRationale = shouldShowShizukuRationale,
        )
    }

    private fun serviceIfAlive(): IUserService? {
        val svc = connection?.serviceOrNull() ?: return null
        return if (svc.asBinder().isBinderAlive) svc else null
    }

    private fun resetStaleStateLocked() {
        connection = null
        activeBackend = null
        activeLeases = 0
        generation++
        nextLeaseId = 1L
        leasedIdsByGeneration.clear()
    }

    suspend fun acquireLease(
        context: Context,
        policy: PrivilegedAcquisitionPolicy = PrivilegedAcquisitionPolicy.AUTO,
    ): PrivilegedAcquisitionResult =
        try {
            mutex.withLock {
                val alive = serviceIfAlive()
                if (alive == null && connection != null) resetStaleStateLocked()
                alive?.let {
                    activeLeases++
                    val token = LeaseToken(generation, nextLeaseId++)
                    leasedIdsByGeneration.getOrPut(token.generation) { mutableSetOf() }
                        .add(token.id)
                    return PrivilegedAcquisitionResult.Acquired(
                        PrivilegedServiceLease(
                            it,
                            token,
                            this,
                        ),
                    )
                }
                when (policy) {
                    PrivilegedAcquisitionPolicy.ROOT_ONLY -> bindRoot(context)
                    PrivilegedAcquisitionPolicy.SHIZUKU_ONLY -> bindShizuku()
                    PrivilegedAcquisitionPolicy.AUTO -> bindAuto(context)
                }
            }
        } catch (e: Throwable) {
            PrivilegedAcquisitionResult.TransientFailure(e)
        }

    private fun connectionOrCreate(): Connection =
        connection ?: Connection().also { connection = it }

    private suspend fun bindAuto(context: Context): PrivilegedAcquisitionResult = when (
        val root = bindRoot(
            context,
        )
    ) {
        is PrivilegedAcquisitionResult.Acquired -> root
        PrivilegedAcquisitionResult.NoPrivilege -> when (val shizuku = bindShizuku()) {
            is PrivilegedAcquisitionResult.Acquired -> shizuku
            PrivilegedAcquisitionResult.NoPrivilege -> PrivilegedAcquisitionResult.NoPrivilege
            is PrivilegedAcquisitionResult.TransientFailure -> shizuku
        }
        is PrivilegedAcquisitionResult.TransientFailure -> when (val shizuku = bindShizuku()) {
            is PrivilegedAcquisitionResult.Acquired -> shizuku
            PrivilegedAcquisitionResult.NoPrivilege -> root
            is PrivilegedAcquisitionResult.TransientFailure -> root
        }
    }

    private fun tryRootAvailable(): Boolean = try {
        Shell.getShell()
        Shell.isAppGrantedRoot() == true
    } catch (_: Throwable) {
        false
    }

    private suspend fun bindRoot(context: Context): PrivilegedAcquisitionResult =
        withContext(Dispatchers.Main) {
            if (!tryRootAvailable()) return@withContext PrivilegedAcquisitionResult.NoPrivilege
            val svc = connectionOrCreate()
            log("Binding RootService")
            RootService.bind(Intent(context, RootUserService::class.java), svc)
            waitForService(svc)?.let {
                activeBackend = PrivilegedBackend.ROOT
                activeLeases = maxOf(activeLeases, 1)
                val token = LeaseToken(generation, nextLeaseId++)
                leasedIdsByGeneration.getOrPut(token.generation) { mutableSetOf() }.add(token.id)
                return@withContext PrivilegedAcquisitionResult.Acquired(
                    PrivilegedServiceLease(
                        it,
                        token,
                        this@PrivilegedServiceManager,
                    ),
                )
            }
            PrivilegedAcquisitionResult.TransientFailure(
                IllegalStateException("Root bind timed out"),
            )
        }

    private suspend fun bindShizuku(): PrivilegedAcquisitionResult = withContext(Dispatchers.Main) {
        if (connection != null && serviceIfAlive() == null) resetStaleStateLocked()
        if (!Shizuku.pingBinder()) return@withContext PrivilegedAcquisitionResult.NoPrivilege
        if (Shizuku.checkSelfPermission() !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext PrivilegedAcquisitionResult.NoPrivilege
        }
        val svc = connectionOrCreate()
        log("Binding Shizuku user service")
        Shizuku.bindUserService(userServiceArgs, svc)
        waitForService(svc)?.let {
            activeBackend = PrivilegedBackend.SHIZUKU
            activeLeases = maxOf(activeLeases, 1)
            val token = LeaseToken(generation, nextLeaseId++)
            leasedIdsByGeneration.getOrPut(token.generation) { mutableSetOf() }.add(token.id)
            return@withContext PrivilegedAcquisitionResult.Acquired(
                PrivilegedServiceLease(
                    it,
                    token,
                    this@PrivilegedServiceManager,
                ),
            )
        }
        PrivilegedAcquisitionResult.TransientFailure(
            IllegalStateException("Shizuku bind timed out"),
        )
    }

    private suspend fun waitForService(connection: Connection): IUserService? =
        withContext(Dispatchers.Main) {
            repeat(40) {
                connection.serviceOrNull()?.takeIf { it.asBinder().isBinderAlive }
                    ?.let { return@withContext it }
                delay(50.milliseconds)
            }
            null
        }

    internal suspend fun releaseLease(token: LeaseToken) {
        var unbindAction: (() -> Unit)? = null
        mutex.withLock {
            val ids = leasedIdsByGeneration[token.generation] ?: return@withLock
            if (!ids.remove(token.id)) return@withLock
            if (ids.isEmpty()) leasedIdsByGeneration.remove(token.generation)
            activeLeases--
            if (activeLeases > 0) return@withLock
            val svc = connection ?: return@withLock
            val backend = activeBackend
            connection = null
            activeBackend = null
            activeLeases = 0
            generation++
            nextLeaseId = 1L
            leasedIdsByGeneration.clear()
            when (backend) {
                PrivilegedBackend.ROOT -> {
                    unbindAction = { RootService.unbind(svc) }
                }
                PrivilegedBackend.SHIZUKU -> {
                    unbindAction = {
                        Shizuku.unbindUserService(
                            userServiceArgs,
                            svc,
                            true,
                        )
                    }
                }
                null -> Unit
            }
        }
        unbindAction?.run {
            withContext(Dispatchers.Main) {
                invoke()
            }
        }
    }
}

private val userServiceArgs: Shizuku.UserServiceArgs by lazy(LazyThreadSafetyMode.NONE) {
    Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("service")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)
}
