package net.liplum.brains

import arc.audio.Sound
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.math.Angles
import arc.math.Interp
import arc.math.Mathf
import arc.util.Time
import mindustry.Vars
import mindustry.entities.bullet.BulletType
import mindustry.gen.Bullet
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.world.blocks.defense.turrets.PowerTurret
import mindustry.world.meta.Stat
import net.liplum.*
import net.liplum.api.brain.*
import net.liplum.lib.Draw
import net.liplum.lib.animations.anims.Anime
import net.liplum.lib.animations.anims.genFramesBy
import net.liplum.lib.animations.anims.randomCurTime
import net.liplum.lib.ui.ammoStats
import net.liplum.math.Polar
import net.liplum.registries.CioSounds
import net.liplum.utils.*

open class Eye(name: String) : PowerTurret(name), IComponentBlock {
    lateinit var normalBullet: BulletType
    lateinit var improvedBullet: BulletType
    @JvmField var normalSounds: Array<Sound> = EmptySounds
    @JvmField var improvedSounds: Array<Sound> = EmptySounds
    @ClientOnly lateinit var BaseTR: TR
    @ClientOnly lateinit var EyeBallTR: TR
    @ClientOnly lateinit var EyelidTR: TR
    @ClientOnly lateinit var PupilTR: TR
    @ClientOnly lateinit var PupilOutsideTR: TR
    @ClientOnly lateinit var HemorrhageTRs: TRs
    @ClientOnly lateinit var BlinkTRs: TRs
    @ClientOnly @JvmField var BlinkDuration = 50f
    @ClientOnly @JvmField var BlinkFrameNum = 9
    @ClientOnly @JvmField var radiusSpeed = 0.1f
    @ClientOnly @JvmField var PupilMax = 4.3f
    @ClientOnly @JvmField var PupilMin = 1.2f
    @ClientOnly @JvmField var outOfCompactTime = 240f
    @ClientOnly @JvmField var continuousShootCheckTime = 10f
    override val upgrades: MutableMap<UpgradeType, Upgrade> = HashMap()
    @ClientOnly @JvmField var maxHemorrhageShotsReq = 5

    init {
        canOverdrive = false
    }

    override fun init() {
        // To prevent accessing a null
        shootType = normalBullet
        checkInit()
        super.init()
    }

    override fun load() {
        super.load()
        BaseTR = this.sub("base")
        EyeBallTR = this.sub("eyeball")
        EyelidTR = this.sub("eyelid")
        BlinkTRs = this.sheet("blink", BlinkFrameNum)
        PupilTR = this.sub("pupil")
        PupilOutsideTR = this.sub("pupil-outside")
        HemorrhageTRs = this.sheet("hemorrhage", 3)
    }

    override fun icons() = arrayOf(
        BaseTR, EyelidTR
    )

    override fun setBars() {
        super.setBars()
        DebugOnly {
            addBrainInfo<EyeBuild>()
        }
    }

    override fun setStats() {
        super.setStats()
        stats.remove(Stat.ammo)
        stats.add(Stat.ammo, ammoStats(Pair(this, normalBullet), Pair(this, improvedBullet)))
        this.addUpgradeComponentStats()
    }

    open inner class EyeBuild : PowerTurretBuild(), IUpgradeComponent {
        //<editor-fold desc="Heimdall">
        override val scale: SpeedScale = SpeedScale()
        override var directionInfo: Direction2 = Direction2.Empty
        override var brain: IBrain? = null
        override val upgrades: Map<UpgradeType, Upgrade>
            get() = this@Eye.upgrades
        //</editor-fold>
        @ClientOnly
        lateinit var blinkAnime: Anime
        init {
            ClientOnly {
                blinkAnime = Anime(
                    BlinkTRs.genFramesBy(BlinkDuration) {
                        Interp.pow2In.apply(it)
                    }
                ).apply {
                    var forward = true
                    isForward = {
                        forward || isShooting || charging()
                    }
                    onEnd = {
                        if (!isShooting && !charging() && !pupilIsApproachingMin) {
                            forward = !forward
                            isEnd = false
                        }
                    }
                    randomCurTime()
                }
            }
        }

        override fun delta(): Float {
            return this.timeScale * Time.delta * speedScale
        }

        override fun updateTile() {
            scale.update()
            super.updateTile()
        }

        override fun onProximityRemoved() {
            super.onProximityRemoved()
            clear()
        }

        override fun remove() {
            super.remove()
            clear()
        }
        @ClientOnly
        val stareAtScreenRadius: Float
            get() = size * Vars.tilesize * 2f
        @ClientOnly
        val sight = Polar(0f, 0f)
        @ClientOnly
        var lastInCombatTime = outOfCompactTime
        @ClientOnly
        val isOutOfCombat: Boolean
            get() = lastInCombatTime >= outOfCompactTime
        val eyeColor: Color = R.C.RedAlert
        @ClientOnly
        var blinkFactor = 1f
        @ClientOnly
        var pupilIsApproachingMin = false
        @ClientOnly
        var continuousShots = 0
        fun checkContinuousShooting() =
            lastInCombatTime < reload + continuousShootCheckTime + shoot.firstShotDelay

        override fun draw() {
            WhenNotPaused {
                blinkAnime.spend(((Mathf.random() * 1) + Time.delta) * blinkFactor)
                lastInCombatTime += Time.delta
                if (!checkContinuousShooting())
                    continuousShots = 0
            }
            BaseTR.Draw(x, y)
            Draw.color()

            Draw.z(Layer.turret)
            val rotationDraw = rotation.draw
            Drawf.shadow(EyeBallTR, x - elevation, y - elevation)
            EyeBallTR.Draw(x, y)
            val radiusSpeed = radiusSpeed * Time.delta
            val consValid = canConsume()

            if (consValid && (isShooting || lastInCombatTime < 40f || charging())) {
                sight.approachR(PupilMax, radiusSpeed * 3f)
                pupilIsApproachingMin = true
            } else {
                sight.approachR(PupilMin, radiusSpeed)
                if (sight.r - PupilMin <= radiusSpeed) {
                    pupilIsApproachingMin = false
                }
            }
            DebugOnly {
                G.dashCircle(x, y, stareAtScreenRadius, alpha = 0.2f)
            }
            WhenNotPaused {
                if (consValid && (isShooting || isControlled || target != null || !isOutOfCombat)) {
                    sight.a = rotation.radian
                } else {
                    val player = Vars.player.unit()
                    if (player.dst(this) > stareAtScreenRadius) {
                        val targetAngle = Angles.angle(x, y, player.x, player.y)
                        sight.a = targetAngle.radian
                    } else {
                        sight.approachR(0f, radiusSpeed * 3f)
                    }
                }
            }
            val pupil = if (consValid && isShooting || sight.r > PupilMin * 1.5f) PupilOutsideTR else PupilTR
            val pupilX = x + sight.x + recoilOffset.x
            val pupilY = y + sight.y + recoilOffset.y
            Draw.mixcol(eyeColor, heat)
            pupil.Draw(pupilX, pupilY, rotationDraw)
            Draw.reset()
            drawHemorrhage()
            blinkAnime.draw(x, y)
            blinkFactor = 1f
        }

        open fun drawHemorrhage() {
            val hemorrhage = HemorrhageTRs.progress(continuousShots.toFloat() / maxHemorrhageShotsReq)
            Draw.alpha(heat)
            hemorrhage.Draw(x, y)
            Draw.color()
        }

        override fun handleBullet(bullet: Bullet, offsetX: Float, offsetY: Float, angleOffset: Float) {
            super.handleBullet(bullet, offsetX, offsetY, angleOffset)
            if (isLinkedBrain)
                improvedSounds.random().at(tile,soundPitchMin)
            else
                normalSounds.random().at(tile)
        }

        override fun hasAmmo() = true
        override fun useAmmo(): BulletType {
            ClientOnly {
                lastInCombatTime = 0f
                if (checkContinuousShooting())
                    continuousShots++
            }
            return if (isLinkedBrain) improvedBullet else normalBullet
        }

        override fun peekAmmo(): BulletType =
            if (isLinkedBrain) improvedBullet else normalBullet
    }
}