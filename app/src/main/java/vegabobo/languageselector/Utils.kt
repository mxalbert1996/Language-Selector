package vegabobo.languageselector

import android.util.Log
import java.util.Locale
import vegabobo.languageselector.ui.screen.appinfo.SingleLocale

fun Locale.capDisplayName(): String = this.getDisplayName(this).replaceFirstChar {
    it.uppercaseChar()
}

fun Set<String>.parseLocales(): MutableList<SingleLocale> = this.mapNotNull {
    try {
        val stringLocale = it.split(",")
        val name = stringLocale[0]
        val tag = stringLocale[1]
        SingleLocale(name, tag)
    } catch (e: Exception) {
        logE(e = e)
        null
    }
}.toMutableList()

fun log(msg: Any) {
    Log.d(BuildConfig.APPLICATION_ID, msg.toString())
}

fun logW(msg: Any) {
    Log.w(BuildConfig.APPLICATION_ID, msg.toString())
}

fun logE(msg: Any = "", e: Throwable? = null) {
    Log.e(BuildConfig.APPLICATION_ID, msg.toString(), e)
}
