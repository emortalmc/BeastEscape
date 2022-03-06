package dev.emortal.beastescape

import dev.emortal.beastescape.game.BeastEscapeGame
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.WhenToRegisterEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.extensions.Extension
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import world.cepi.kstom.Manager

class BeastEscapeExtension : Extension() {
    override fun initialize() {
        GameManager.registerGame<BeastEscapeGame>(
            eventNode,
            "beastescape",
            Component.text("Beast Escape", NamedTextColor.RED, TextDecoration.BOLD),
            true,
            true,
            WhenToRegisterEvents.GAME_START,
            GameOptions(
                minPlayers = 2,
                maxPlayers = 6,
                showScoreboard = true
            )
        )

        val dimensionType = DimensionType.builder(NamespaceID.from("ftf"))/*.ambientLight(0.05f)*/.build()
        Manager.dimensionType.addDimension(dimensionType)

        logger.info("[${origin.name}] Initialized!")
    }

    override fun terminate() {
        logger.info("[${origin.name}] Terminated!")
    }
}