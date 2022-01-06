package dev.emortal.beastescape.util

import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Entity
import net.minestom.server.network.packet.server.play.EntityAnimationPacket

fun PacketGroupingAudience.goRed(entity: Entity) {
    val packet = EntityAnimationPacket(entity.entityId, EntityAnimationPacket.Animation.TAKE_DAMAGE)
    sendGroupedPacket(packet)
}