package dev.emortal.beastescape.game

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent

class Computer(
    val game: BeastEscapeGame,
    val position: Point
) {

    companion object {
        val secondsToHack = 35
    }

    val guiMap = mutableMapOf<Player, ComputerGUI>()

    var hackedPercent = 0f
    val hackers = mutableListOf<Player>()

    init {
        game.computerMap[position] = this
    }

    fun addHacker(player: Player) {
        val gui = ComputerGUI(player, this)
        player.openInventory(gui.inventory)
        hackers.add(player)
        game.computerHackerMap[player] = this
    }
    fun removeHacker(player: Player) {
        player.closeInventory()
        hackers.remove(player)
        guiMap[player]?.destroy()
        game.computerHackerMap.remove(player)
    }

    fun destroy() {
        guiMap.forEach {
            it.key.closeInventory()
            it.value.destroy()
        }
        guiMap.clear()
        hackers.clear()
    }

    fun hack() {
        hackers.forEach {
            it.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 1f))
        }

        game.instance.setBlock(position, Block.AIR)


        game.hackedComputers++
        game.scoreboard?.updateLineContent(
            "computersLeft",
            Component.text()
                .append(Component.text("PCBs left: ", NamedTextColor.GRAY))
                .append(Component.text(game.computersToExit - game.hackedComputers, NamedTextColor.RED))
                .build()
        )

        if (game.hackedComputers >= game.computersToExit) {
            game.canHackDoors = true
            game.playSound(Sound.sound(SoundEvent.BLOCK_BEACON_POWER_SELECT, Sound.Source.MASTER, 2f, 0.5f), Sound.Emitter.self())
            game.sendMessage(
                Component.text()
                    .append(Component.text("All PCBs have been hacked, the doors can now be opened!", NamedTextColor.RED))
            )

            game.scoreboard?.updateLineContent(
                "computersLeft",
                Component.text()
                    .append(Component.text("The doors are now unlockable!", NamedTextColor.RED))
                    .build()
            )
        }

        destroy()
    }

}
