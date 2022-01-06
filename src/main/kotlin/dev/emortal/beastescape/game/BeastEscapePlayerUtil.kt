package dev.emortal.beastescape.game

import net.minestom.server.entity.Player
import net.minestom.server.tag.Tag
import world.cepi.kstom.Manager
import java.util.*

val knockedTag = Tag.Byte("knocked")
val heldPlayerTag = Tag.String("heldPlayer")

var Player.isKnocked: Boolean
    get() = hasTag(knockedTag)
    set(value) {
        if (!value) {
            removeTag(knockedTag)
            return
        }
        setTag(knockedTag, 1)
    }

var Player.heldPlayer: Player?
    get() = if (hasTag(heldPlayerTag)) Manager.connection.getPlayer(UUID.fromString(getTag(heldPlayerTag))) else null
    set(value) {
        if (value == null) {
            removeTag(heldPlayerTag)
            return
        }
        setTag(heldPlayerTag, value.uuid.toString())
    }

fun Player.cleanup() {
    removeTag(knockedTag)
    removeTag(heldPlayerTag)
}