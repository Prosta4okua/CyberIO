@file:JvmName("GameH")

package net.liplum.mdt

import arc.Core
import arc.util.Log
import mindustry.Vars
import mindustry.gen.Teamc
import net.liplum.common.Condition
import java.lang.annotation.Inherited
import kotlin.annotation.AnnotationTarget.*

/**
 * It indicates this should be called or accessed only on Physical Client.
 * You should wrap this with [ClientOnly] or [ClientOnlyOn].
 * If a certain target isn't annotated this, it can be called on Physical Server(headless) safely.
 * ## Use case
 * 1. On properties or fields, you shouldn't access them, it may provide wrong data or even crash the game.
 * 2. On functions, you shouldn't call them, it can crash the game.
 * 3. On classes or objects, you must never load them into class loader, the static initialization can crash the game.
 */
@Retention(AnnotationRetention.SOURCE)
@Inherited
@MustBeDocumented
annotation class ClientOnly
/**
 * It indicates this should be called or accessed only on Logical Server.
 * You should wrap this with [ServerOnly].
 */
@Retention(AnnotationRetention.SOURCE)
@Inherited
@MustBeDocumented
annotation class ServerOnly
/**
 * It indicates this should be called or accessed only on Physical Server(headless).
 * You should wrap this with [HeadlessOnly].
 * If a certain target isn't annotated this, it can be called on Physical Client safely.
 * ## Use case
 * 1. On properties or fields, you shouldn't access them, it may provide wrong data or even crash the game.
 * 2. On functions, you shouldn't call them, it can crash the game.
 * 3. On classes or objects, you must never load them into class loader, the static initialization can crash the game.
 */
@Retention(AnnotationRetention.SOURCE)
@Inherited
@MustBeDocumented
annotation class HeadlessOnly
/**
 * It indicates this will send data packet to synchronize no matter which Server/Client.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(
    FUNCTION,
    PROPERTY_SETTER,
    CONSTRUCTOR,
    PROPERTY,
)
@Inherited
@MustBeDocumented
annotation class SendDataPack(
    val callChain: Array<String> = [],
)
/**
 * It indicates this will be called by a function which handles data packet.
 */
@Retention(AnnotationRetention.SOURCE)
@Inherited
@MustBeDocumented
annotation class CalledBySync
/**
 * It indicates something in the vanilla will be overwritten
 */
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class OverwriteVanilla(val value: String = "")
/**
 * Runs codes only on Physical Client
 */
inline fun ClientOnly(func: () -> Unit): Boolean {
    if (!Vars.headless) {
        func()
        return true
    }
    return false
}

inline fun DesktopOnly(func: () -> Unit): Boolean {
    if (!Vars.mobile) {
        func()
        return true
    }
    return false
}

inline fun MobileOnly(func: () -> Unit): Boolean {
    if (Vars.mobile) {
        func()
        return true
    }
    return false
}

val IsClient = Condition { !Vars.headless }
val IsServer = Condition {
    val net = Vars.net
    net.server() || !net.active()
}
val IsLocal = Condition {
    val net = Vars.net
    !net.server() && !net.active()
}
val IsSteam = Condition { Vars.steam }
inline fun <reified T> T.ClientOnlyOn(func: T.() -> Unit): T {
    if (!Vars.headless) {
        func()
    }
    return this
}
/**
 * Runs codes only on Logical Server
 */
inline fun ServerOnly(func: () -> Unit): Boolean {
    val net = Vars.net
    if (net.server() || !net.active()) {
        func()
        return true
    }
    return false
}

inline fun NetClientOnly(func: () -> Unit): Boolean {
    if (Vars.net.client()) {
        func()
        return true
    }
    return false
}

inline fun PortraitModeOnly(func: () -> Unit): Boolean {
    if (Core.graphics.isPortrait) {
        func()
        return true
    }
    return false
}
inline fun LandscapeModeOnly(func: () -> Unit): Boolean {
    if (!Core.graphics.isPortrait) {
        func()
        return true
    }
    return false
}
/**
 * Runs codes only on Logical Server
 */
inline fun OnlyLocal(func: () -> Unit): Boolean {
    val net = Vars.net
    if (!net.server() && !net.active()) {
        func()
    }
    return false
}

fun IsServer(): Boolean {
    val net = Vars.net
    return net.server() || !net.active()
}
/**
 * Runs codes only on Physical Server
 */
inline fun HeadlessOnly(func: () -> Unit): Boolean {
    if (Vars.headless) {
        func()
    }
    return false
}

inline fun SteamOnly(func: () -> Unit): Boolean {
    if (Vars.steam) {
        func()
        return true
    }
    return false
}

inline fun UnsteamOnly(func: () -> Unit): Boolean {
    if (!(Vars.steam)) {
        func()
        return true
    }
    return false
}

inline infix fun Boolean.Else(func: () -> Unit) {
    if (!this) {
        func()
    }
}

inline fun WhenNotPaused(func: () -> Unit) {
    if (!Vars.state.isPaused) {
        func()
    }
}
/**
 * If an exception is thrown, it doesn't crash the game but outputs log.
 */
inline fun safeCall(msg: String? = null, func: () -> Unit) {
    try {
        func()
    } catch (e: Throwable) {
        if (msg != null) Log.err(msg)
        Log.err(e)
    }
}
/**
 * If an exception is thrown, it doesn't crash the game but outputs log.
 */
inline fun safeCall(func: () -> Unit) {
    try {
        func()
    } catch (e: Throwable) {
        Log.err(e)
    }
}
/**
 * If an exception is thrown, it doesn't crash the game without any log.
 */
inline fun safeCallSilent(func: () -> Unit) {
    try {
        func()
    } catch (_: Throwable) {
    }
}

inline fun Teamc.WhenTheSameTeam(func: () -> Unit): Boolean {
    if (team() == Vars.player.team()) {
        func()
        return true
    }
    return false
}