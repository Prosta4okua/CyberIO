package net.liplum.holo

import arc.Core
import arc.Events
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.math.Angles
import arc.math.Mathf
import arc.struct.Seq
import arc.util.Time
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.game.EventType.UnitDestroyEvent
import mindustry.gen.UnitEntity
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.logic.LAccess
import mindustry.type.UnitType
import mindustry.world.blocks.ConstructBlock.ConstructBuild
import mindustry.world.blocks.payloads.BuildPayload
import mindustry.world.blocks.payloads.Payload
import mindustry.world.blocks.power.PowerGraph
import net.liplum.S
import net.liplum.common.persistence.*
import net.liplum.holo.HoloProjector.HoloProjectorBuild
import plumy.core.Serialized
import net.liplum.mdt.ClientOnly
import net.liplum.mdt.mixin.PayloadMixin
import net.liplum.mdt.render.G
import net.liplum.mdt.utils.TE
import net.liplum.mdt.utils.build
import net.liplum.mdt.utils.exists
import net.liplum.mdt.utils.hasShields
import net.liplum.registry.CioFluids
import net.liplum.registry.EntityRegistry

open class HoloUnit : UnitEntity(), PayloadMixin, IRevisionable {
    override val revisionID = 0
    override var payloadPower: PowerGraph? = null
    override var payloads = Seq<Payload>()
    override val unitType: UnitType
        get() = super.type
    @Serialized
    @JvmField var time = 0f
    val holoType: HoloUnitType
        get() = type as HoloUnitType
    open val lifespan: Float
        get() = holoType.lifespan
    open val overageDmgFactor: Float
        get() = holoType.overageDmgFactor
    open val lose: Float
        get() = holoType.lose
    open val restLifePercent: Float
        get() = (1f - (time / lifespan)).coerceIn(0f, 1f)
    open val restLife: Float
        get() = (lifespan - time).coerceIn(0f, lifespan)
    open val loseMultiplierWhereMissing: Float
        get() = holoType.loseMultiplierWhereMissing
    @Serialized
    var projectorPos: Int = -1
    val isProjectorMissing: Boolean
        get() = !projectorPos.build.exists

    open fun setProjector(projector: HoloProjectorBuild) {
        projectorPos = projector.pos()
    }

    override fun toString() = "HoloUnit#$id"
    override fun update() {
        /* Pick up everything
        if (isPlayer) {
            val build = tileOn().build
            if (build != null)
                pickup(build)
        }*/
        if (type is HoloUnitType) {
            val loseMultiplier: Float
            if (isProjectorMissing) {
                loseMultiplier = loseMultiplierWhereMissing
                projectorPos = -1
            } else {
                loseMultiplier = 1f
            }
            time += Time.delta * loseMultiplier
            updateBySuper()
            val lose = lose * loseMultiplier
            var damage = lose
            val overage = time - lifespan
            if (overage > 0) {
                damage += overage * lose * overageDmgFactor
            }
            damageByHoloDimming(damage)
        } else {
            updateBySuper()
        }
    }

    open fun updateBySuper() {
        super.update()
        updatePayload()
    }

    override fun destroy() {
        if (this.isAdded) {
            Events.fire(UnitDestroyEvent(this))
            for (mount in mounts) {
                if (mount.weapon.shootOnDeath && (!mount.weapon.bullet.killShooter || !mount.shoot)) {
                    mount.reload = 0.0f
                    mount.shoot = true
                    mount.weapon.update(this, mount)
                }
            }
            for (ability in abilities) {
                ability.death(this)
            }
            this.remove()
        }
    }

    override fun cap(): Int {
        team.updateHoloCapacity()
        return team.holoCapacity
    }

    open fun damageByHoloDimming(amount: Float) {
        val hasShields = this.hasShields
        if (hasShields) {
            shieldAlpha = 1.0f
        }
        health -= amount
        if (health <= 0.0f && !dead) {
            if (hasShields) {
                (abilities.find {
                    it is HoloForceField
                } as? HoloForceField)?.let {
                    val cacheRange = it.realRange(this)
                    Time.run(30f) {
                        HoloFx.shieldBreak.at(
                            x, y,
                            cacheRange,
                            S.Hologram, this
                        )
                    }
                }
            }
            kill()
        }
    }

    override fun draw() {
        drawBuilding()
        drawMining()
        drawStatusEffect()
        type.draw(this)
        drawRuvikTip()
    }

    override fun killed() {
        super.killed()
        dropLastPayload()
    }

    override fun drawBuildingBeam(px: Float, py: Float) {
        val active = activelyBuilding()
        if (!active && lastActive == null) return
        Draw.z(Layer.flyingUnit)
        val plan = if (active) buildPlan() else lastActive
        val tile = Vars.world.tile(plan.x, plan.y)
        if (tile == null || !within(plan, if (Vars.state.rules.infiniteResources) Float.MAX_VALUE else type.buildRange)) {
            return
        }
        val size = if (plan.breaking) if (active) tile.block().size else lastSize else plan.block.size
        val tx = plan.drawx()
        val ty = plan.drawy()
        Lines.stroke(1.0f, S.Hologram)
        Draw.z(Layer.buildBeam)
        Draw.alpha(buildAlpha)
        if (!active && tile.build !is ConstructBuild) {
            Fill.square(plan.drawx(), plan.drawy(), size * Vars.tilesize / 2.0f)
        }
        Drawf.buildBeam(px, py, tx, ty, Vars.tilesize * size / 2.0f)
        Fill.square(px, py, 1.8f + Mathf.absin(Time.time, 2.2f, 1.1f), rotation + 45)
        Draw.reset()
        Draw.z(Layer.flyingUnit)
    }

    open fun drawStatusEffect() {
        for (entry in statuses) {
            entry.effect.draw(this, entry.time)
        }
    }

    open fun drawMining() {
        if (mining()) {
            val focusLen = hitSize / 2.0f + Mathf.absin(Time.time, 1.1f, 0.5f)
            val swingScl = 12.0f
            val swingMag = 1.0f
            val px = x + Angles.trnsx(rotation, focusLen)
            val py = y + Angles.trnsy(rotation, focusLen)
            val ex = mineTile.worldx() + Mathf.sin(Time.time + 48.0f, swingScl, swingMag)
            val ey = mineTile.worldy() + Mathf.sin(Time.time + 48.0f, swingScl + 2.0f, swingMag)
            Draw.z(115.1f)
            Draw.color(S.Hologram)
            Draw.alpha(0.45f)
            Drawf.laser(Core.atlas.find("minelaser"), Core.atlas.find("minelaser-end"), px, py, ex, ey, 0.75f)
            if (this.isLocal) {
                Lines.stroke(1.0f)
                Lines.poly(mineTile.worldx(), mineTile.worldy(), 4, 4.0f * Mathf.sqrt2, Time.time)
            }
            Draw.color()
        }
    }
    @ClientOnly
    var ruvikTipAlpha = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    open fun drawRuvikTip() {
        if (type is HoloUnitType) {
            val holoType = holoType
            if (holoType.enableRuvikTip && isLocal) {
                if (isShooting) {
                    ruvikTipAlpha += 2f / holoType.ruvikShootingTipTime
                } else {
                    ruvikTipAlpha -= 0.5f / holoType.ruvikShootingTipTime
                }
                if (ruvikTipAlpha > 0f) {
                    G.dashCircleBreath(x, y, holoType.ruvikTipRange, color = S.Hologram, alpha = ruvikTipAlpha)
                }
            }
        }
    }

    override fun updatePayload() {
        val projector = projectorPos.TE<HoloProjectorBuild>()
        if (projector?.power != null)
            payloadPower = projector.power?.graph

        for (pay in payloads) {
            if (pay is BuildPayload && pay.build.power != null) {
                val payPower = payloadPower ?: PowerGraph()
                payloadPower = payPower
                pay.build.power.graph = null
                payPower.add(pay.build)
            }
        }
        payloadPower?.update()
        for (pay in payloads) {
            pay.set(x, y, rotation())
            tryTransferCyberionInto(pay)
            pay.update(self(), null)
        }
    }

    fun tryTransferCyberionInto(payload: Payload) {
        val projector = projectorPos.TE<HoloProjectorBuild>() ?: return
        val type = type as? HoloUnitType ?: return
        if (payload is BuildPayload) {
            val build = payload.build
            if (build.acceptLiquid(projector, CioFluids.cyberion)) {
                val amount = type.sacrificeCyberionAmount
                time += type.sacrificeLifeFunc(amount)
                build.handleLiquid(projector, CioFluids.cyberion, amount)
            }
        }
    }

    override fun classId(): Int {
        return EntityRegistry[javaClass]
    }

    override fun read(_read_: Reads) {
        super.read(_read_)
        // Since 8, use cache reader instead of vanilla
        ReadFromCache(_read_, revisionID) {
            time = f()
            projectorPos = i()
            Warp {
                readPayload(this)
            }
        }
    }

    override fun write(_write_: Writes) {
        super.write(_write_)
        // Since 8, use cache writer instead of vanilla
        WriteIntoCache(_write_, revisionID) {
            f(time)
            i(projectorPos)
            Wrap {
                writePayload(this)
            }
        }
    }
    // Sync doesn't need revision
    override fun readSync(read: Reads) {
        super.readSync(read)
        time = read.f()
        projectorPos = read.i()
        readPayload(read)
    }
    // Sync doesn't need revision
    override fun writeSync(write: Writes) {
        super.writeSync(write)
        write.f(time)
        write.i(projectorPos)
        writePayload(write)
    }

    override fun sense(sensor: LAccess): Double {
        return when (sensor) {
            LAccess.progress -> (1f - restLifePercent).toDouble()
            else -> super.sense(sensor)
        }
    }
}