package net.liplum.blocks.prism

import arc.func.Prov
import arc.math.Mathf
import arc.struct.EnumSet
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.gen.Building
import mindustry.world.Block
import mindustry.world.meta.BlockFlag
import mindustry.world.meta.BlockGroup
import net.liplum.R
import net.liplum.Var
import net.liplum.blocks.prism.Prism.PrismBuild
import net.liplum.common.util.bundle
import net.liplum.mdt.ClientOnly
import net.liplum.mdt.WhenNotPaused
import net.liplum.mdt.animation.anims.Animation
import net.liplum.mdt.animation.anims.AnimationObj
import net.liplum.mdt.animation.anims.pingPong
import net.liplum.mdt.render.G
import net.liplum.mdt.render.drawSurroundingRect
import net.liplum.mdt.render.smoothPlacing
import net.liplum.mdt.ui.bars.AddBar
import net.liplum.mdt.utils.*

open class PrismObelisk(name: String) : Block(name) {
    @JvmField var prismType: Prism? = null
    @ClientOnly lateinit var BlinkAnim: Animation
    /**
     * The area(tile xy) indicates the surrounding prism can be linked.
     */
    @JvmField @ClientOnly var indicateAreaExtension = 2f
    @JvmField @ClientOnly var BlinkFrames = 6
    @JvmField @ClientOnly var BlinkDuration = 20f
    @ClientOnly @JvmField var maxSelectedCircleTime = Var.SurroundingRectTime

    init {
        buildType = Prov { ObeliskBuild() }
        absorbLasers = true
        update = true
        solid = true
        group = BlockGroup.turrets
        flags = EnumSet.of(BlockFlag.turret)
        noUpdateDisabled = true
        canOverdrive = false
    }

    override fun load() {
        super.load()
        BlinkAnim = this.autoAnim("blink", BlinkFrames, BlinkDuration)
    }

    override fun setBars() {
        super.setBars()
        AddBar<ObeliskBuild>(R.Bar.LinkedN,
            {
                if (isLinked) R.Bar.Linked.bundle
                else R.Bar.Unlinked.bundle
            },
            { Prism.animatedColor.color },
            { if (linked != -1) 1f else 0f }
        )
    }

    override fun drawPlace(x: Int, y: Int, rotation: Int, valid: Boolean) {
        super.drawPlace(x, y, rotation, valid)
        drawSurroundingRect(
            x, y, indicateAreaExtension * smoothPlacing(maxSelectedCircleTime),
            if (valid) Prism.animatedColor.color else R.C.RedAlert,
        ) {
            it.block == prismType && !it.isDiagonalTo(this, x, y)
        }
        drawPlaceText(subBundle("tip"), x, y, valid)
    }

    open inner class ObeliskBuild : Building() {
        var linked: Int = -1
        val isLinked: Boolean
            get() = linked != -1
        val prism: PrismBuild?
            get() = linked.TE()
        /**
         * Left->Down->Right->Up
         */
        @JvmField var prismOrient = 0
        @ClientOnly lateinit var BlinkObjs: Array<AnimationObj>

        init {
            ClientOnly {
                BlinkObjs = Array(4) {
                    BlinkAnim.gen().pingPong().apply { sleepInstantly() }
                }
            }
        }

        override fun onProximityUpdate() {
            super.onProximityUpdate()
            val mayLinked = linked
            if (mayLinked != -1 && !mayLinked.TE<PrismBuild>().exists) {
                linked = -1
            }
        }

        open fun canLink(prism: PrismBuild) = prismType == prism.block && linked == -1
        open fun link(prism: PrismBuild) {
            if (canLink(prism)) {
                linked = prism.pos()
            }
        }

        open fun unlink() {
            linked = -1
        }

        override fun draw() {
            super.draw()
            WhenNotPaused {
                val d = delta()
                for ((i, obj) in BlinkObjs.withIndex()) {
                    if (linked == -1) {
                        obj.sleep()
                    } else
                        obj.wakeUp()
                    obj.spend(d + Mathf.random())
                    obj.draw(x, y, i * 90f)
                }
            }
        }

        override fun drawSelect() {
            prism?.apply {
                G.selected(this, Prism.animatedColor.color)
            }
        }

        override fun read(read: Reads, revision: Byte) {
            super.read(read, revision)
            linked = read.i()
        }

        override fun write(write: Writes) {
            super.write(write)
            write.i(linked)
        }
    }
}
