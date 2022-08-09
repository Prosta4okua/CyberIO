package net.liplum.holo

import arc.Events
import arc.func.Prov
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.math.Angles
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Strings.autoFixed
import arc.util.Time
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.game.EventType.UnitCreateEvent
import mindustry.gen.*
import mindustry.gen.Unit
import mindustry.graphics.Layer
import mindustry.graphics.Pal
import mindustry.logic.LAccess
import mindustry.type.*
import mindustry.ui.Fonts
import mindustry.ui.Styles
import mindustry.world.Block
import mindustry.world.consumers.ConsumeItemDynamic
import mindustry.world.meta.BlockGroup
import mindustry.world.meta.Stat
import net.liplum.DebugOnly
import net.liplum.R
import net.liplum.S
import net.liplum.UndebugOnly
import net.liplum.common.shader.use
import net.liplum.common.util.bundle
import net.liplum.common.util.percentI
import plumy.core.Serialized
import net.liplum.mdt.*
import net.liplum.mdt.consumer.ConsumeFluidDynamic
import net.liplum.mdt.render.Draw
import net.liplum.mdt.ui.addItemSelectorDefault
import net.liplum.mdt.ui.bars.AddBar
import net.liplum.mdt.ui.bars.removeItemsInBar
import net.liplum.mdt.utils.*
import net.liplum.registry.CioFluids.cyberion
import net.liplum.registry.SD
import net.liplum.ui.addTable
import kotlin.math.max

open class HoloProjector(name: String) : Block(name) {
    @JvmField var plans: ArrayList<HoloPlan> = ArrayList()
    @JvmField var itemCapabilities: IntArray = IntArray(0)
    @JvmField var holoUnitCapacity = 8
    @JvmField var powerUse = 3f
    @ClientOnly @JvmField var projectorShrink = 5f
    @ClientOnly @JvmField var projectorCenterRate = 3f
    /**
     * For vertices of plan
     */
    @ClientOnly
    val vecs = arrayOf(Vec2(), Vec2(), Vec2(), Vec2())

    init {
        buildType = Prov { HoloProjectorBuild() }
        solid = true
        update = true
        hasPower = true
        hasItems = true
        updateInUnits = true
        alwaysUpdateInUnits = true
        hasLiquids = true
        group = BlockGroup.units
        saveConfig = true
        configurable = true
        sync = true
        commandable = true
        config(Integer::class.java) { obj: HoloProjectorBuild, plan ->
            obj.setPlan(plan.toInt())
        }
        configClear { obj: HoloProjectorBuild ->
            obj.setPlan(-1)
        }
    }

    override fun init() {
        consume(ConsumeFluidDynamic<HoloProjectorBuild> {
            val plan = it.curPlan ?: return@ConsumeFluidDynamic LiquidStack.empty
            plan.req.liquidArray
        })
        consume(ConsumeItemDynamic<HoloProjectorBuild> {
            val plan = it.curPlan ?: return@ConsumeItemDynamic ItemStack.empty
            plan.req.items
        })
        consumePowerCond<HoloProjectorBuild>(powerUse) {
            it.curPlan != null
        }
        itemCapabilities = IntArray(ItemTypeAmount())
        for (plan in plans) {
            for (itemReq in plan.itemReqs) {
                itemCapabilities[itemReq.item.ID] =
                    max(
                        itemCapabilities[itemReq.item.id.toInt()],
                        itemReq.amount * 2
                    )
                itemCapacity = max(itemCapacity, itemReq.amount * 2)
            }
        }
        val cyberionCapacity = plans.maxBy { it.req.cyberion * 60f }.req.cyberion * 2
        liquidCapacity = max(liquidCapacity, cyberionCapacity)
        super.init()
    }

    override fun setBars() {
        super.setBars()
        UndebugOnly {
            removeItemsInBar()
        }
        DebugOnly {
            AddBar<HoloProjectorBuild>("progress",
                { "${"bar.progress".bundle}: ${progress.percentI}" },
                { S.Hologram },
                { progress }
            )
        }.Else {
            AddBar<HoloProjectorBuild>("progress",
                { "bar.progress".bundle },
                { S.Hologram },
                { progress }
            )
        }
        AddBar<HoloProjectorBuild>(R.Bar.Vanilla.UnitsN,
            {
                val curPlan = curPlan
                if (curPlan == null)
                    "[lightgray]${Iconc.cancel}"
                else {
                    val unitType = curPlan.unitType
                    R.Bar.Vanilla.UnitCapacity.bundle(
                        Fonts.getUnicodeStr(unitType.name),
                        team.data().countType(unitType),
                        team.getStringHoloCap()
                    )
                }
            },
            { Pal.power },
            {
                val curPlan = curPlan
                curPlan?.unitType?.pctOfTeamOwns(team) ?: 0f
            }
        )
    }

    protected val Int.plan: HoloPlan?
        get() = if (this < 0 || this >= plans.size)
            null
        else
            plans[this]
    var hoveredInfo: Table? = null

    open inner class HoloProjectorBuild : Building() {
        @Serialized
        var planIndex: Int = -1
        val curPlan: HoloPlan?
            get() = planIndex.plan
        @Serialized
        var progressTime = 0f
        var commandPos: Vec2? = null
        override fun block(): HoloProjector = this@HoloProjector
        val progress: Float
            get() {
                val plan = curPlan
                return if (plan != null)
                    (progressTime / plan.time).coerceIn(0f, 1f)
                else
                    0f
            }

        override fun updateTile() {
            if (!canConsume()) return
            val plan = curPlan ?: return
            progressTime += edelta()

            if (progressTime >= plan.time) {
                val unitType = plan.unitType
                if (unitType.canCreateHoloUnitIn(team)) {
                    projectUnit(unitType)
                    consume()
                    progressTime = 0f
                }
            }
        }
        @CalledBySync
        open fun setPlan(plan: Int) {
            var order = plan
            if (order < 0 || order >= plans.size) {
                order = -1
            }
            if (order == planIndex) return
            planIndex = order
            val p = curPlan
            progressTime = if (p != null)
                progressTime.coerceAtMost(p.time)
            else
                0f
            rebuildHoveredInfo()
        }

        override fun shouldConsume() = enabled && curPlan != null && progress < 1f
        override fun buildConfiguration(table: Table) {
            val options = Seq.with(plans).map {
                it.unitType
            }.filter {
                it.unlockedNow() && !it.isBanned
            }
            if (options.any()) {
                table.addItemSelectorDefault(this@HoloProjector, options,
                    { curPlan?.unitType }
                ) { unit: UnitType? ->
                    val selected = plans.indexOfFirst {
                        it.unitType == unit
                    }
                    configure(selected)
                }
            } else {
                table.table(Styles.black3) { t: Table ->
                    t.add("@none").color(Color.lightGray)
                }
            }
        }

        override fun onConfigureBuildTapped(other: Building): Boolean {
            if (this == other) {
                deselect()
                configure(null)
                return false
            }
            return true
        }

        open fun rebuildHoveredInfo() {
            try {
                val info = hoveredInfo
                if (info != null) {
                    info.clear()
                    display(info)
                }
            } catch (_: Exception) {
                // Maybe null pointer or cast exception
            }
        }

        @JvmField var lastUnitInPayload: MdtUnit? = null
        fun findTrueHoloProjectorSource(): HoloProjectorBuild {
            val unit = lastUnitInPayload
            if (unit is HoloUnit) {
                val trueProjector = unit.projectorPos.TE<HoloProjectorBuild>()
                if (trueProjector != null)
                    return trueProjector
            }
            return this
        }

        override fun updatePayload(unitHolder: Unit?, buildingHolder: Building?) {
            lastUnitInPayload = unitHolder
            super.updatePayload(unitHolder, buildingHolder)
        }

        override fun config(): Any? = planIndex
        open fun projectUnit(unitType: HoloUnitType) {
            val unit = unitType.create(team)
            if (unit is HoloUnit) {
                unit.set(x, y)
                ServerOnly {
                    unit.add()
                }
                unit.setProjector(findTrueHoloProjectorSource())
                val commandPos = commandPos
                if (commandPos != null && unit.isCommandable) {
                    unit.command().commandPosition(commandPos)
                }
                Events.fire(UnitCreateEvent(unit, this))
            }
        }
        @ClientOnly
        var alpha = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
            }
        @ClientOnly
        var lastPlan: HoloPlan? = curPlan
        @ClientOnly
        var projecting = 0f
        override fun draw() {
            super.draw()
            val curPlan = curPlan
            val delta = if (canConsume() && curPlan != null)
                0.015f
            else
                -0.015f
            alpha += delta * Time.delta
            val planDraw = curPlan ?: lastPlan
            if (lastPlan != curPlan)
                lastPlan = curPlan
            if (alpha <= 0.01f) return
            if (planDraw != null) {
                SD.Hologram.use {
                    val type = planDraw.unitType
                    it.alpha = (progress * 1.2f * alpha).coerceAtMost(1f)
                    it.flickering = it.DefaultFlickering - (1f - progress) * 0.4f
                    if (type.ColorOpacity > 0f)
                        it.blendFormerColorOpacity = type.ColorOpacity
                    if (type.HoloOpacity > 0f) {
                        it.blendHoloColorOpacity = type.HoloOpacity
                    }
                    type.fullIcon.Draw(x, y)
                }
            }
            WhenNotPaused {
                if (progress < 1f && !inPayload) {
                    projecting += delta()
                }
            }
            val rotation = projecting
            val size = block.size * Vars.tilesize / projectorCenterRate
            // tx and ty control the position of bottom edge
            val tx = x
            val ty = y
            Lines.stroke(1.0f)
            Draw.color(S.HologramDark)
            Draw.alpha(alpha)
            // the floating of center
            val focusLen = 3.8f + Mathf.absin(projecting, 3.0f, 0.6f)
            val px = x + Angles.trnsx(rotation, focusLen)
            val py = y + Angles.trnsy(rotation, focusLen)
            val shrink = projectorShrink
            // the vertices
            vecs[0].set(tx - size, ty - size) // left-bottom
            vecs[1].set(tx + size, ty - size) // right-bottom
            vecs[2].set(tx - size, ty + size) // left-top
            vecs[3].set(tx + size, ty + size) // right-top
            Draw.z(Layer.buildBeam)
            if (Vars.renderer.animateShields) {
                Fill.tri(px, py, vecs[0].x + shrink, vecs[0].y, vecs[1].x - shrink, vecs[1].y) // bottom
                Fill.tri(px, py, vecs[2].x + shrink, vecs[2].y, vecs[3].x - shrink, vecs[3].y) // up
                Fill.tri(px, py, vecs[0].x, vecs[0].y + shrink, vecs[2].x, vecs[2].y - shrink) // left
                Fill.tri(px, py, vecs[1].x, vecs[1].y + shrink, vecs[3].x, vecs[3].y - shrink) // right
            } else {
                // bottom
                Lines.line(px, py, vecs[0].x + shrink, vecs[0].y)
                Lines.line(px, py, vecs[1].x - shrink, vecs[1].y)
                // up
                Lines.line(px, py, vecs[2].x + shrink, vecs[2].y)
                Lines.line(px, py, vecs[3].x - shrink, vecs[3].y)
                // left
                Lines.line(px, py, vecs[0].x, vecs[0].y + shrink)
                Lines.line(px, py, vecs[2].x, vecs[3].y - shrink)
                // right
                Lines.line(px, py, vecs[1].x, vecs[1].y + shrink)
                Lines.line(px, py, vecs[3].x, vecs[3].y - shrink)
            }
            Draw.reset()
        }

        override fun acceptLiquid(source: Building, liquid: Liquid) =
            liquid == cyberion && liquids[cyberion] < liquidCapacity

        override fun getMaximumAccepted(item: Item) =
            itemCapabilities[item.id.toInt()]

        override fun acceptItem(source: Building, item: Item): Boolean {
            val curPlan = curPlan ?: return false
            return items[item] < getMaximumAccepted(item) && item in curPlan.req
        }

        override fun created() {
            team.updateHoloCapacity(this)
        }

        override fun add() {
            super.add()
            team.updateHoloCapacity(this)
        }

        override fun updateProximity() {
            super.updateProximity()
            team.updateHoloCapacity(this)
        }

        override fun remove() {
            super.remove()
            team.updateHoloCapacity(this)
        }

        override fun read(read: Reads, revision: Byte) {
            super.read(read, revision)
            planIndex = read.b().toInt()
            progressTime = read.f()
        }

        override fun write(write: Writes) {
            super.write(write)
            write.b(planIndex)
            write.f(progressTime)
        }

        override fun senseObject(sensor: LAccess): Any? {
            return when (sensor) {
                LAccess.config -> planIndex
                else -> super.sense(sensor)
            }
        }

        override fun sense(sensor: LAccess): Double {
            return when (sensor) {
                LAccess.progress -> progress.toDouble()
                else -> super.sense(sensor)
            }
        }

        override fun getCommandPosition(): Vec2? {
            return commandPos
        }

        override fun onCommand(target: Vec2) {
            commandPos = target
        }
    }

    override fun setStats() {
        super.setStats()
        stats.remove(Stat.itemCapacity)

        stats.add(Stat.output) { stat: Table ->
            stat.row()
            for (plan in plans) {
                stat.addTable {
                    background = Tex.whiteui
                    setColor(Pal.darkestGray)
                    if (plan.unitType.isBanned) {
                        image(Icon.cancel).color(Pal.remove).size(40f)
                        return@addTable
                    }
                    if (plan.unitType.unlockedNow()) {
                        image(plan.unitType.uiIcon).size(40f).pad(10f).left()
                        addTable {
                            add(plan.unitType.localizedName).left()
                            row()
                            add("${autoFixed(plan.time / 60f, 1)} ${"unit.seconds".bundle}")
                                .color(Color.lightGray)
                        }.left()
                        addTable {
                            right()
                            add(autoFixed(plan.req.cyberion * 60f, 1))
                                .color(cyberion.color).padLeft(12f).left()
                            image(cyberion.uiIcon).size((8 * 3).toFloat())
                                .padRight(2f).right()
                        }.right().grow().pad(10f)
                    } else {
                        image(Icon.lock).color(Pal.darkerGray).size(40f)
                    }
                }.growX().pad(5f)
                stat.row()
            }
        }
    }
}
