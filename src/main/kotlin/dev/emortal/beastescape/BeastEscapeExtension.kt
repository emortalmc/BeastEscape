package dev.emortal.beastescape

import dev.emortal.beastescape.game.BeastEscapeGame
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.WhenToRegisterEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.extensions.Extension

class BeastEscapeExtension : Extension() {
    override fun initialize() {
        GameManager.registerGame<BeastEscapeGame>(
            eventNode,
            "beastescape",
            Component.text("Beast Escape", NamedTextColor.RED),
            true,
            WhenToRegisterEvents.GAME_START,
            GameOptions(
                minPlayers = 2,
                maxPlayers = 6,
                showScoreboard = true
            )
        )

        logger.info("[${origin.name}] Initialized!")
    }

    override fun terminate() {
        logger.info("[${origin.name}] Terminated!")
    }
}