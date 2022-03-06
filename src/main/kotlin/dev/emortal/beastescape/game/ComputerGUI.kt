package dev.emortal.beastescape.game

import dev.emortal.beastescape.util.showGlowingMarker
import dev.emortal.immortal.util.MinestomRunnable
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemHideFlag
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.noItalic
import world.cepi.kstom.item.item
import world.cepi.kstom.util.get
import world.cepi.kstom.util.playSound
import world.cepi.kstom.util.set
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor

class ComputerGUI(val player: Player, val computer: Computer) {

    val inventory: Inventory = Inventory(InventoryType.CHEST_3_ROW, Component.text("PCB"))

    var canFailFromExit = false

    val secondTask = object : MinestomRunnable(timer = computer.game.timer, repeat = Duration.ofSeconds(1)) {

        override fun run() {
            computer.hackedPercent += 1f / Computer.secondsToHack.toFloat()

            inventory.title = Component.text()
                .append(Component.text("PCB ", NamedTextColor.BLACK))
                .append(Component.text("${floor(computer.hackedPercent * 100).toInt()}%", NamedTextColor.DARK_GRAY))
                .build()

            if (computer.hackedPercent >= 1f) {
                computer.hack()
                failTask?.cancel()
                stepWaitTask?.cancel()
                cancel()
                return
            }
        }
    }

    var failTask: Task? = null
    var stepWaitTask: Task? = null

    init {
        computer.guiMap[player] = this

        if (computer.hackedPercent <= 0f) inventory.fill(
            item(Material.BLACK_STAINED_GLASS_PANE) {
                displayName(Component.text("Click the green squares!", NamedTextColor.WHITE, TextDecoration.BOLD).noItalic())
                hideFlag(*ItemHideFlag.values())
            }
        )

        sleep(if (computer.hackedPercent > 0f) 0 else 2000) {
            step()

            inventory.addInventoryCondition { player, slot, clickType, inventoryConditionResult ->
                val itemstack = inventory[slot]

                inventoryConditionResult.isCancel = true
                inventoryConditionResult.clickedItem = ItemStack.AIR
                inventoryConditionResult.cursorItem = ItemStack.AIR

                if (itemstack.material == Material.LIME_STAINED_GLASS_PANE) {
                    player.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.MASTER, 1f, 1f))
                    step()
                } else {
                    if (itemstack.material == Material.RED_STAINED_GLASS_PANE || itemstack.material == Material.BLACK_STAINED_GLASS_PANE) return@addInventoryCondition
                    fail()
                }
            }
        }
    }

    private fun step() {
        canFailFromExit = false
        val rand = ThreadLocalRandom.current()

        inventory.clear()

        failTask?.cancel()
        failTask = null

        stepWaitTask = sleep(rand.nextLong(2000, 5000)) {
            failTask = Manager.scheduler.buildTask { fail() }.delay(Duration.ofSeconds(3)).schedule()

            canFailFromExit = true

            inventory[rand.nextInt(inventory.size)] = item(Material.LIME_STAINED_GLASS_PANE) {
                displayName(Component.empty())
                hideFlag(*ItemHideFlag.values())
            }
        }
    }

    fun destroy() {
        secondTask.cancel()
        stepWaitTask?.cancel()
        failTask?.cancel()
    }

    fun fail() {
        computer.game.showGlowingMarker(computer.position, computer.game.instance, NamedTextColor.RED)
        computer.game.playSound(Sound.sound(SoundEvent.BLOCK_GLASS_BREAK, Sound.Source.MASTER, 10f, 1f), computer.position)

        destroy()

        player.sendMessage(
            Component.text()
                .append(Component.text("You failed while hacking a computer, the beast has been notified", NamedTextColor.RED))
                .append(Component.text(" (you should run)", NamedTextColor.DARK_GRAY))
        )

        inventory.fill(
            item(Material.RED_STAINED_GLASS_PANE) {
                inventory.title = Component.text("FAIL", NamedTextColor.RED, TextDecoration.BOLD)
                displayName(Component.text("FAIL", NamedTextColor.RED, TextDecoration.BOLD).noItalic())
                hideFlag(*ItemHideFlag.values())
            }
        )

        sleep(1000) {
            player.playSound(Sound.sound(SoundEvent.BLOCK_CHEST_CLOSE, Sound.Source.MASTER, 1f, 1f))
            inventory.removeViewer(player)
            computer.removeHacker(player)
        }
    }

    fun Inventory.fill(item: ItemStack) {
        val items = itemStacks
        items.fill(item)
        copyContents(items)
    }

    private fun sleep(millis: Long, runnable: Runnable): Task =
        Manager.scheduler.buildTask {
            runnable.run()
        }.delay(Duration.ofMillis(millis)).schedule()

}