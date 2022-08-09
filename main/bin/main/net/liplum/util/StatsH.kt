package net.liplum.util

import arc.scene.ui.Label
import arc.struct.OrderedMap
import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.meta.Stat
import mindustry.world.meta.StatCat
import mindustry.world.meta.StatValue
import plumy.core.UseReflection
import net.liplum.common.util.getF
import net.liplum.mdt.utils.subBundle

@UseReflection
fun Block.addPowerUseStats() {
    val map = stats.getF<OrderedMap<StatCat, OrderedMap<Stat, Seq<StatValue>>>?>("map")
    if (map != null && map.get(StatCat.power)?.containsKey(Stat.powerUse) == true) {
        stats.remove(Stat.powerUse)
    }
    stats.add(Stat.powerUse) {
        val l = Label(subBundle("stats.power-use"))
        it.add(l)
    }
}