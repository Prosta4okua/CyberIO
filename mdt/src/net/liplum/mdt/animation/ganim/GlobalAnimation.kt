package net.liplum.mdt.animation.ganim

import arc.func.Cons
import arc.graphics.g2d.TextureRegion
import arc.math.Mathf
import arc.util.Time
import mindustry.game.EventType
import net.liplum.annotations.Only
import net.liplum.annotations.Subscribe
import net.liplum.common.util.progress
import net.liplum.mdt.ClientOnly

typealias GlobalAnimationIndexer = GlobalAnimation.() -> TextureRegion

open class GlobalAnimation(
    val duration: Float,
    val setTR: Cons<TextureRegion>,
) : IGlobalAnimation {
    var allFrames: Array<TextureRegion>? = null
    val frames: Array<TextureRegion>
        get() = allFrames!!
    var frameIndexer: GlobalAnimationIndexer = loopIndexer
    override val canUpdate: Boolean
        get() = CanPlay
    var lastTR: TextureRegion? = null
    protected fun getCurTR(): TextureRegion {
        return frameIndexer()
    }
    @ClientOnly
    override fun update() {
        if (canUpdate && allFrames != null) {
            val curTR = getCurTR()
            if (curTR != lastTR) {
                lastTR = curTR
                this.setTR.get(curTR)
            }
        }
    }

    companion object {
        val loopIndexer: GlobalAnimationIndexer = {
            val progress = Time.globalTime % duration / duration //percent
            frames.progress(progress)
        }
        val randomSelectIndexr: GlobalAnimationIndexer = {
            frames[Mathf.randomSeed((Time.globalTime / 3f).toLong(), 0, frames.size - 1)]
        }
        var CanPlay = false
        val updateTasks = HashSet<IGlobalAnimation>()
        @ClientOnly
        @JvmStatic
        @Subscribe(EventType.Trigger.update, Only.client)
        fun animate() {
            if (CanPlay) {
                for (task in updateTasks)
                    task.update()
            }
        }

        fun GlobalAnimation.useRandom(): GlobalAnimation {
            this.frameIndexer = randomSelectIndexr
            return this
        }

        fun GlobalAnimation.register(): GlobalAnimation {
            updateTasks.add(this)
            return this
        }
        @ClientOnly
        fun globalPlay() {
            if (CanPlay) {
                for (task in updateTasks)
                    task.update()
            }
        }
    }
}