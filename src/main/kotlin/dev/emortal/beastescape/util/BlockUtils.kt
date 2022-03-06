package dev.emortal.beastescape.util

import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.network.packet.server.play.EffectPacket
import java.util.concurrent.ThreadLocalRandom

private val rand = ThreadLocalRandom.current()

fun PacketGroupingAudience.sendBlockDamage(point: Point, destroyStage: Byte) {
    sendGroupedPacket(BlockBreakAnimationPacket(rand.nextInt(1000), point, destroyStage))
}

fun PacketGroupingAudience.breakBlock(point: Point, block: Block) {
    sendGroupedPacket(EffectPacket(2001/*Block break + block break sound*/, point, block.stateId().toInt(), false))
}