package dev.emortal.beastescape.util

import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.AttachEntityPacket

fun PacketGroupingAudience.showLead(holder: Player?, entity: Entity) {
    val packet = AttachEntityPacket(entity, holder)
    sendGroupedPacket(packet)
}