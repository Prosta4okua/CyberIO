package net.liplum.mdt.ui.bars

import arc.graphics.Color
import arc.scene.ui.layout.Table
import arc.util.Time
import mindustry.Vars
import mindustry.gen.Building
import mindustry.ui.Bar
import mindustry.world.Block
import net.liplum.mdt.utils.ID
import net.liplum.mdt.utils.LiquidTypeAmount

fun Block.removeItemsInBar() {
    this.removeBar("items")
}

fun Block.removeLiquidInBar() {
    this.removeBar("liquid")
}

fun Block.removeHealthInBar() {
    this.removeBar("health")
}

inline fun <reified T : Building> Block.AddBar(
    key: String,
    crossinline name: T.() -> String,
    crossinline color: T.() -> Color,
    crossinline fraction: T.() -> Float,
    crossinline config: Bar.() -> Unit = {},
) {
    addBar<T>(key) {
        Bar(
            { it.name() },
            { it.color() },
            { it.fraction() }
        ).apply(config)
    }
}

inline fun Block.addBar(
    key: String,
    crossinline name: Building.() -> String,
    crossinline color: Building.() -> Color,
    crossinline fraction: Building.() -> Float,
    crossinline config: Bar.() -> Unit = {},
    ) {
    addBar<Building>(key) {
        Bar(
            { it.name() },
            { it.color() },
            { it.fraction() }
        ).apply(config)
    }
}

const val minIntervalBarDisplay = 10f
fun Block.genAllLiquidBars(): Array<(Building) -> Bar> =
    Array(LiquidTypeAmount()) { i ->
        val liquid = Vars.content.liquids()[i]
        {
            Bar({ liquid.localizedName },
                { liquid.barColor() },
                { it.liquids[liquid] / liquidCapacity }
            )
        }
    }

inline fun Building.appendDisplayLiquidsDynamic(
    table: Table,
    allLiquidBars: Array<(Building) -> Bar>,
    crossinline superDisplayBars: (Table) -> Unit
) {
    table.update {
        if (Time.time % minIntervalBarDisplay < Time.delta) {
            table.clearChildren()
            superDisplayBars(table)
            for (liquid in Vars.content.liquids()) {
                if (liquids[liquid] > 0f) {
                    val bar = allLiquidBars[liquid.ID](this)
                    table.add(bar).growX()
                    table.row()
                }
            }
        }
    }
}