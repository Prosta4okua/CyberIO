package net.liplum.blocks.prism

import arc.math.Mathf
import arc.struct.EnumSet
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.gen.Building
import mindustry.world.Block
import mindustry.world.meta.BlockFlag
import mindustry.world.meta.BlockGroup
import net.liplum.R
import net.liplum.blocks.prism.Prism.PrismBuild
import net.liplum.lib.utils.bundle
import net.liplum.mdt.ClientOnly
import net.liplum.mdt.WhenNotPaused
import net.liplum.mdt.animations.anims.Animation
import net.liplum.mdt.animations.anims.AnimationObj
import net.liplum.mdt.animations.anims.pingPong
import net.liplum.mdt.render.drawSurroundingRect
import net.liplum.mdt.ui.bars.AddBar
import net.liplum.mdt.utils.TE
import net.liplum.mdt.utils.autoAnim
import net.liplum.mdt.utils.exists
import net.liplum.mdt.utils.isDiagonalTo

open class PrismObelisk(name: String) : Block(name) {
    @JvmField var prismType: Prism? = null
    @ClientOnly lateinit var BlinkAnim: Animation
    /**
     * The area(tile xy) indicates the surrounding prism can be linked.
     */
    @JvmField @ClientOnly var indicateAreaExtension = 2f
    @JvmField @ClientOnly var BlinkFrames = 6
    @JvmField @ClientOnly var BlinkDuration = 20f

    init {
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
                if (linked != -1)
                    R.Bar.Linked.bundle()
                else
                    R.Bar.NoLink.bundle()
            }, AutoRGBx,
            { if (linked != -1) 1f else 0f }
        )
    }

    override fun drawPlace(x: Int, y: Int, rotation: Int, valid: Boolean) {
        super.drawPlace(x, y, rotation, valid)
        drawSurroundingRect(x, y, indicateAreaExtension, if (valid) R.C.GreenSafe else R.C.RedAlert) {
            it.block == prismType && !it.isDiagonalTo(this, x, y)
        }
        drawPlaceText("$contentType.$name.tip".bundle, x, y, valid)
    }

    open inner class ObeliskBuild : Building() {
        var linked: Int = -1
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
