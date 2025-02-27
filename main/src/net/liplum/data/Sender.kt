package net.liplum.data

import arc.func.Prov
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.TextureRegion
import arc.math.Mathf
import arc.math.geom.Point2
import arc.struct.ObjectSet
import arc.util.Eachable
import arc.util.Time
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.entities.units.BuildPlan
import mindustry.gen.Building
import mindustry.graphics.Pal
import mindustry.logic.LAccess
import mindustry.type.Item
import mindustry.world.meta.BlockGroup
import net.liplum.DebugOnly
import net.liplum.R
import net.liplum.Var
import net.liplum.api.cyber.*
import net.liplum.blocks.AniedBlock
import net.liplum.common.Changed
import net.liplum.data.Sender.SenderBuild
import net.liplum.mdt.CalledBySync
import net.liplum.mdt.ClientOnly
import net.liplum.mdt.SendDataPack
import net.liplum.mdt.animation.anims.Animation
import net.liplum.mdt.animation.anis.AniState
import net.liplum.mdt.animation.anis.config
import net.liplum.mdt.render.Draw
import net.liplum.mdt.render.DrawOn
import net.liplum.mdt.render.SetColor
import net.liplum.mdt.ui.bars.AddBar
import net.liplum.mdt.utils.*
import plumy.core.Serialized
import plumy.core.arc.Tick
import plumy.core.assets.TR
import plumy.core.math.isZero

private typealias AniStateS = AniState<Sender, SenderBuild>

open class Sender(name: String) : AniedBlock<Sender, SenderBuild>(name) {
    @ClientOnly lateinit var BaseTR: TR
    @ClientOnly lateinit var HighlightTR: TR
    @ClientOnly lateinit var UpArrowTR: TR
    @ClientOnly lateinit var CrossTR: TR
    @ClientOnly lateinit var NoPowerTR: TR
    @ClientOnly lateinit var UnconnectedTR: TR
    @ClientOnly lateinit var UploadAnim: Animation
    @JvmField var UploadAnimFrameNumber = 7
    @JvmField var UploadAnimDuration = 30f
    @JvmField val TransferTimer = timers++
    /**
     * The max range when trying to connect. -1f means no limit.
     */
    @JvmField var maxRange = -1f
    @ClientOnly @JvmField var maxSelectedCircleTime = Var.SelectedCircleTime
    @ClientOnly @JvmField var SendingTime = 60f

    init {
        buildType = Prov { SenderBuild() }
        solid = true
        update = true
        acceptsItems = true
        configurable = true
        group = BlockGroup.transportation
        canOverdrive = false
        schematicPriority = 20
        unloadable = false
        callDefaultBlockDraw = false
        /**
         * For connect
         */
        config(Integer::class.java) { it: SenderBuild, receiver ->
            it.setReceiverFromRemote(receiver.toInt())
        }
        configClear<SenderBuild> {
            it.receiverPos = null
        }
    }

    override fun load() {
        super.load()
        BaseTR = this.sub("base")
        HighlightTR = this.sub("highlight")
        UpArrowTR = this.inMod("rs-up-arrow")
        CrossTR = this.inMod("rs-cross")
        UnconnectedTR = this.inMod("rs-unconnected")
        NoPowerTR = this.inMod("rs-no-power")
        UploadAnim = this.autoAnimInMod("rs-up-arrow", UploadAnimFrameNumber, UploadAnimDuration)
    }

    override fun drawPlace(x: Int, y: Int, rotation: Int, valid: Boolean) {
        super.drawPlace(x, y, rotation, valid)
        drawPlacingMaxRange(x, y, maxRange, R.C.Sender)
    }

    override fun drawPlanRegion(plan: BuildPlan, list: Eachable<BuildPlan>) {
        super.drawPlanRegion(plan, list)
        drawPlanMaxRange(plan.x, plan.y, maxRange, R.C.Sender)
    }

    override fun setBars() {
        super.setBars()
        DebugOnly {
            addReceiverInfo<SenderBuild>()
            AddBar<SenderBuild>("last-sending",
                { "Last Send: ${lastSendingTime.toInt()}" },
                { Pal.bar },
                { lastSendingTime / SendingTime }
            )
        }
    }

    override fun setStats() {
        super.setStats()
        addLinkRangeStats(maxRange)
        addMaxReceiverStats(1)
    }

    val sharedReceiverSet = ObjectSet<Int>()

    open inner class SenderBuild : AniedBuild(), IDataSender {
        override val maxRange = this@Sender.maxRange
        @ClientOnly var lastSendingTime: Tick = 0f
            set(value) {
                field = value.coerceAtLeast(0f)
            }
        @ClientOnly
        open val isBlocked: Boolean
            get() = lastSendingTime > SendingTime
        @set:CalledBySync
        @Serialized
        var receiverPos: Point2? = null
            set(value) {
                if (field != value) {
                    var curBuild = field.dr()
                    curBuild?.onDisconnectFrom(this)
                    field = value
                    curBuild = value.dr()
                    curBuild?.onConnectTo(this)
                }
            }
        val receiver: IDataReceiver?
            get() = receiverPos.dr()
        @CalledBySync
        fun setReceiverFromRemote(pos: PackedPos) {
            val unpacked = Point2.unpack(pos)
            receiverPos = if (unpacked.dr().exists) unpacked else null
        }
        /**
         * @param relative the relative position
         * @return
         */
        fun resolveRelativePos(relative: Point2): Point2 {
            val res = relative.cpy()
            res.x += this.tile.x
            res.y += this.tile.y
            return res // now it's absolute position
        }
        /**
         * Consider this block as (0,0)
         */
        fun genRelativePos(): Point2? {
            val abs = receiverPos?.cpy() ?: return null
            abs.x -= this.tile.x
            abs.y -= this.tile.y
            val relative = abs// now it's relative
            return relative
        }

        fun checkReceiverPos() {
            if (receiverPos == null) return
            if (!receiverPos.dr().exists) {
                receiverPos = null
            }
        }

        var lastTileChange = -2
        override fun updateTile() {
            // Check connection and queue only when any block changed
            if (lastTileChange != Vars.world.tileChanges) {
                lastTileChange = Vars.world.tileChanges
                checkReceiverPos()
            }
            ClientOnly {
                lastSendingTime += Time.delta
                val target = receiver?.receiverColor?.let { if (it == R.C.Receiver) R.C.Sender else it } ?: R.C.Sender
                if (target != targetSenderColor) {
                    lastSenderColor = Changed(old = targetSenderColor)
                    targetSenderColor = target
                }
            }
        }

        override fun toString() =
            "Sender#$id(->$receiverPos)"

        override val connectedReceivers: ObjectSet<Int>
            get() = sharedReceiverSet.apply {
                clear()
                receiverPos?.let {
                    add(it.pack())
                }
            }

        override fun handleItem(source: Building, item: Item) {
            if (!canConsume()) {
                return
            }
            val reb = receiver
            if (reb != null) {
                sendDataTo(reb, item, 1)
                ClientOnly {
                    lastSendingTime = 0f
                }
            }
        }
        @ClientOnly
        var lastSenderColor = Changed.empty<Color>()
        @ClientOnly
        var targetSenderColor = R.C.Sender
        @ClientOnly
        override val senderColor: Color
            get() = transitionColor(lastSenderColor, targetSenderColor)
        @ClientOnly
        override fun drawConfigure() {
            super.drawConfigure()
            this.drawDataNetGraph()
            drawConfiguringMaxRange()
        }
        @ClientOnly
        override fun drawSelect() {
            drawSelectedMaxRange()
        }
        @ClientOnly
        @SendDataPack
        override fun onConfigureBuildTapped(other: Building): Boolean {
            if (this == other) {
                deselect()
                configure(null)
                return false
            }
            if (other.tileEquals(receiverPos)) {
                configure(null)
                return false
            }
            if (other is IDataReceiver) {
                if (maxRange > 0f && other.dst(this) >= maxRange) {
                    postOverRangeOn(other)
                    return false
                } else {
                    if (other.acceptConnectionTo(this)) {
                        receiver?.let { disconnectFromSync(it) }
                        connectToSync(other)
                    } else {
                        postFullSenderOn(other)
                    }
                }
                return false
            }
            return true
        }

        override fun acceptItem(source: Building, item: Item): Boolean {
            if (!canConsume()) {
                return false
            }
            val reb = receiver
            return reb?.getAcceptedAmount(this, item)?.isAccepted() ?: false
        }

        override fun write(write: Writes) {
            super.write(write)
            write.i(receiverPos?.pack() ?: -1)
        }

        override fun read(read: Reads, revision: Byte) {
            super.read(read, revision)
            val packPos = read.i()
            receiverPos = if (packPos != -1) packPos.unpack() else null
        }
        @SendDataPack
        override fun connectToSync(receiver: IDataReceiver) {
            val target = receiver.building
            if (!target.tileEquals(receiverPos)) {
                configure(target.pos())
            }
        }
        @SendDataPack
        override fun disconnectFromSync(receiver: IDataReceiver) {
            if (receiver.building.tileEquals(receiverPos)) {
                configure(null)
            }
        }

        override fun control(type: LAccess, p1: Any?, p2: Double, p3: Double, p4: Double) {
            when (type) {
                LAccess.shoot ->
                    if (!p2.isZero && p1 is IDataReceiver) connectToSync(p1)
                else -> super.control(type, p1, p2, p3, p4)
            }
        }

        override fun control(type: LAccess, p1: Double, p2: Double, p3: Double, p4: Double) {
            when (type) {
                LAccess.shoot -> {
                    val receiver = buildAt(p1, p2)
                    if (!p3.isZero && receiver is IDataReceiver) connectToSync(receiver)
                }
                else -> super.control(type, p1, p2, p3, p4)
            }
        }

        override fun sense(sensor: LAccess): Double {
            return when (sensor) {
                LAccess.shootX -> receiver.tileXd
                LAccess.shootY -> receiver.tileYd
                else -> super.sense(sensor)
            }
        }
        @ClientOnly @JvmField
        var highlightAlpha = 1f
        override fun fixedDraw() {
            BaseTR.DrawOn(this)
            if (aniStateM.curState == IdleAni) {
                highlightAlpha = Mathf.approach(highlightAlpha, 1f, 0.01f)
                Draw.alpha(highlightAlpha)
                HighlightTR.DrawOn(this)
                Draw.color()
            } else {
                highlightAlpha = Mathf.approach(highlightAlpha, Var.RsSlightHighlightAlpha, 0.01f)
                Draw.alpha(highlightAlpha)
                HighlightTR.DrawOn(this)
                Draw.color()
            }
        }
    }

    @ClientOnly lateinit var IdleAni: AniStateS
    @ClientOnly lateinit var UploadAni: AniStateS
    @ClientOnly lateinit var BlockedAni: AniStateS
    @ClientOnly lateinit var NoPowerAni: AniStateS
    @ClientOnly
    override fun genAniState() {
        IdleAni = addAniState("Idle")
        UploadAni = addAniState("Upload") {
            UploadAnim.draw(Color.green, x, y)
        }
        BlockedAni = addAniState("Blocked") {
            SetColor(R.C.Stop)
            UpArrowTR.Draw(x, y)
        }
        NoPowerAni = addAniState("NoPower") {
            NoPowerTR.Draw(x, y)
        }
    }
    @ClientOnly
    override fun genAniConfig() {
        config {
            // Idle
            From(IdleAni) To UploadAni When {
                val reb = receiver
                reb != null
            } To NoPowerAni When {
                !canConsume()
            }
            // Upload
            From(UploadAni) To IdleAni When {
                receiverPos == null
            } To BlockedAni When {
                val reb = receiver
                reb != null && isBlocked
            } To NoPowerAni When {
                !canConsume()
            }
            // Blocked
            From(BlockedAni) To IdleAni When {
                receiverPos == null
            } To UploadAni When {
                val reb = receiver
                reb != null && !isBlocked
            } To NoPowerAni When {
                !canConsume()
            }
            // NoPower
            From(NoPowerAni) To IdleAni When {
                canConsume()
            }
        }
    }
}