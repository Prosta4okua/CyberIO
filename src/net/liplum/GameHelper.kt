package net.liplum

import mindustry.Vars

inline fun ClientOnly(func: () -> Unit) {
    if (!Vars.headless) {
        func()
    }
}

inline fun ServerOnly(func: () -> Unit) {
    if (Vars.headless) {
        func()
    }
}

inline fun CanGlobalAnimationPlay(func: () -> Unit) {
    if (CioMod.CanGlobalAnimationPlay) {
        func()
    }
}

inline fun CanAniStateLoad(func: () -> Unit) {
    if (CioMod.CanAniStateLoad) {
        func()
    }
}