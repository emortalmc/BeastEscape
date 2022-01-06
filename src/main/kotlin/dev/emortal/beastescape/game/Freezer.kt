package dev.emortal.beastescape.game

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.AreaEffectCloudMeta
import net.minestom.server.timer.Task
import world.cepi.kstom.Manager
import world.cepi.kstom.util.asVec
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.showParticle
import java.time.Duration

class Freezer(
    val game: BeastEscapeGame,
    val heldPlayer: Player,
    val position: Point,
    val bossBar: BossBar,
    val freezingTask: Task,
) {

    private val hologram: Entity = Entity(EntityType.AREA_EFFECT_CLOUD)
    private val freezeEntity: Entity =
        Entity(EntityType.AREA_EFFECT_CLOUD) // Entity for player to ride so they cannot move

    private val particlePosition = position.add(0.5, 3.0, 0.5).asVec()
    private val particleTask = Manager.scheduler.buildTask {
        game.instance.showParticle(
            Particle.Companion.particle(
                type = ParticleType.SNOWFLAKE,
                count = 10,
                data = OffsetAndSpeed(0.2f, 0f, 0.2f, 0f)
            ),
            particlePosition
        )
    }.repeat(Duration.ofMillis(150)).schedule()

    init {
        game.showBossBar(bossBar)
        game.frozenTeam.add(heldPlayer)
        heldPlayer.isGlowing = true

        val freezeMeta = freezeEntity.entityMeta as AreaEffectCloudMeta
        freezeMeta.setNotifyAboutChanges(false)
        freezeMeta.radius = 0f
        freezeMeta.isHasNoGravity = true
        freezeMeta.setNotifyAboutChanges(true)
        freezeEntity.setInstance(game.instance, position.add(0.5, 0.5, 0.5))

        freezeEntity.addPassenger(heldPlayer)

        val holoMeta = hologram.entityMeta as AreaEffectCloudMeta
        holoMeta.setNotifyAboutChanges(false)
        holoMeta.radius = 0f
        holoMeta.customName = Component.text("RIGHT CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD)
        holoMeta.isCustomNameVisible = true
        holoMeta.isHasNoGravity = true
        holoMeta.setNotifyAboutChanges(true)
        hologram.updateViewableRule { game.survivorsTeam.players.contains(it) && !game.frozenTeam.players.contains(it) }
        hologram.setInstance(game.instance, position.add(0.5, 2.0, 0.5))

        game.freezerMap[heldPlayer] = this
    }

    fun destroy() {
        freezeEntity.removePassenger(heldPlayer)

        hologram.remove()
        freezingTask.cancel()
        particleTask.cancel()
        game.hideBossBar(bossBar)
        heldPlayer.isGlowing = false
        game.frozenTeam.remove(heldPlayer)

        game.freezerMap.remove(heldPlayer)
    }

}
