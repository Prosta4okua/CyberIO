package net.liplum

import mindustry.Vars
import net.liplum.utils.AniUtil

annotation class ClientOnly
annotation class V6
annotation class V7
/**
 * Runs codes only on Physical Client
 */
inline fun ClientOnly(func: () -> Unit) {
    if (!Vars.headless) {
        func()
    }
}
/**
 * Runs codes only on Logical Server
 */
inline fun ServerOnly(func: () -> Unit) {
    val net = Vars.net
    if (net.server() || !net.active()) {
        func()
    }
}

inline fun WhenCanGlobalAnimationPlay(func: () -> Unit) {
    if (CioMod.CanGlobalAnimationPlay) {
        func()
    }
}

inline fun WhenCanAniStateLoad(func: () -> Unit) {
    if (CioMod.CanAniStateLoad && AniUtil.needUpdateAniStateM()) {
        func()
    }
}

inline fun DebugOnly(func: () -> Unit) {
    if (CioMod.DebugMode) {
        func()
    }
}