package dev.emortal.beastescape.util

import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.metadata.golem.ShulkerMeta
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.PluginMessagePacket
import net.minestom.server.utils.binary.BinaryWriter
import world.cepi.kstom.Manager
import java.awt.Color
import java.time.Duration

fun PacketGroupingAudience.showMarker(point: Point, color: Color, message: String = "") {
    val writer = BinaryWriter()
    writer.writeBlockPosition(point)
    writer.writeInt(color.rgb)
    writer.writeSizedString(message)
    writer.writeInt(15000)

    val packet = PluginMessagePacket("minecraft:debug/game_test_add_marker", writer.toByteArray())
    sendGroupedPacket(packet)
}

fun PacketGroupingAudience.showGlowingMarker(point: Point, instance: Instance, color: NamedTextColor) {
    val team = Manager.team.createBuilder("shulkerTeam")
        .teamColor(color)
        .build()

    val entity = LivingEntity(EntityType.SHULKER)
    val meta = entity.entityMeta as ShulkerMeta
    meta.isInvisible = true
    meta.isHasGlowingEffect = true

    entity.team = team
    entity.updateViewableRule { this.players.contains(it) }
    entity.scheduleRemove(Duration.ofSeconds(15))
    entity.setInstance(instance, point)
}