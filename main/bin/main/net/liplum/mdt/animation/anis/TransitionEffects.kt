@file:JvmName("TransitionEffects")

package net.liplum.mdt.animation.anis

import arc.math.Interp
import arc.math.Mathf
import plumy.core.arc.invoke
import net.liplum.mdt.render.ALPHA

val None: TransitionEffect = { _, _, cur ->
    cur()
}
val LinerFade: TransitionEffect = { progress, last, cur ->
    val p = Mathf.lerp(progress, progress * progress, 0.5f)
    val lastAlpha = 1f - p
    if (lastAlpha > 0) {
        ALPHA = lastAlpha
        last()
    }
    if (p > 0) {
        ALPHA = p
        cur()
    }
}
val SmoothFade: TransitionEffect = { progress, last, cur ->
    val p = Interp.smoother(progress)
    val lastAlpha = 1f - p
    if (lastAlpha > 0) {
        ALPHA = lastAlpha
        last()
    }
    if (p > 0) {
        ALPHA = p
        cur()
    }
}
