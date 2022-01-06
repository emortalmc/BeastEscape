package dev.emortal.beastescape.game

import dev.emortal.beastescape.util.goRed
import dev.emortal.beastescape.util.showLead
import dev.emortal.immortal.game.Game
import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.Team
import dev.emortal.immortal.util.SuperflatGenerator
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.attribute.Attribute
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.sound.SoundEvent
import net.minestom.server.utils.NamespaceID
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.item.item
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.playSound
import java.time.Duration
import kotlin.math.pow

class BeastEscapeGame(gameOptions: GameOptions) : Game(gameOptions) {

    val survivorsTeam =
        registerTeam(
            Team(
                "survivors",
                NamedTextColor.WHITE,
                nameTagVisibility = TeamsPacket.NameTagVisibility.NEVER
            )
        )
    val beastTeam = registerTeam(
        Team(
            "beast",
            NamedTextColor.RED,
            nameTagVisibility = TeamsPacket.NameTagVisibility.NEVER
        )
    )
    val frozenTeam = registerTeam(
        Team(
            "frozen",
            NamedTextColor.AQUA,
            nameTagVisibility = TeamsPacket.NameTagVisibility.ALWAYS
        )
    )

    val leashHelperMap = hashMapOf<Player, Entity>()
    val freezerMap = hashMapOf<Player, Freezer>()

    var computersToHack = 4 // TODO: Could use list of points instead?

    override fun playerJoin(player: Player) {

    }

    override fun playerLeave(player: Player) {
        if (beastTeam.players.contains(player)) {
            if (beastTeam.players.size < 2) {
                victory(WinReason.BEAST_LEFT)
            }
        }
        if (survivorsTeam.players.contains(player)) {
            if (survivorsTeam.players.size < 2) {
                victory(WinReason.SURVIVORS_LEFT)
            }
        }

        freezerMap[player]?.destroy()
        player.cleanup()
    }

    override fun gameStarted() {

        instance.setBlock(5, 5, 0, Block.IRON_BLOCK)
        instance.setBlock(-5, 5, 0, Block.LAPIS_BLOCK)

        instance.time = 18000

        var picked: Player
        (0..15).forEach {
            Manager.scheduler.buildTask {
                picked = players.random()

                playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_SNARE, Sound.Source.BLOCK, 1f, 1f))
                showTitle(
                    Title.title(
                        Component.text(
                            picked.username,
                            TextColor.lerp((it / 15f), NamedTextColor.WHITE, NamedTextColor.RED)
                        ),
                        Component.empty(),
                        Title.Times.of(
                            Duration.ZERO, Duration.ZERO, Duration.ofSeconds(1)
                        )
                    )
                )


                if (it == 15) {
                    playSound(
                        Sound.sound(
                            SoundEvent.ENTITY_ENDER_DRAGON_GROWL,
                            Sound.Source.HOSTILE,
                            1f,
                            1f
                        )
                    )

                    beastTeam.add(picked)
                    players.forEach { plr ->
                        if (plr == picked) return@forEach
                        survivorsTeam.add(plr)
                    }

                    Manager.scheduler.buildTask {
                        setupGame()
                    }.delay(Duration.ofSeconds(2)).schedule()
                }
            }.delay(Duration.ofMillis((it * 4.0).pow(2.0).toLong())).schedule()
        }
    }

    fun setupGame() {
        val survivorsMessage = Component.text()
            .append(Component.text("${" ".repeat(15)}YOU ARE A SURVIVOR", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("\n\nHack computers, open the gates and escape the beast!", NamedTextColor.GRAY))
            .append(Component.text("\nThe beast can see if you fail to hack a computer", NamedTextColor.GRAY))

        val beastMessage = Component.text()
            .append(Component.text("${" ".repeat(15)}YOU ARE THE BEAST", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("\n\nCatch survivors and place them in a freezing chamber!", NamedTextColor.GRAY))
            .append(
                Component.text(
                    "\nSurvivors can help other survivors out of a freezing chamber.",
                    NamedTextColor.GRAY
                )
            )
            .append(
                Component.text(
                    "\nYou are notified of a player's location when they fail to hack a computer.",
                    NamedTextColor.GRAY
                )
            )

        survivorsTeam.sendMessage(survivorsMessage)

        beastTeam.players.forEach {
            it.sendMessage(beastMessage)
            it.inventory.setItemStack(0, item(Material.IRON_AXE))
        }


    }

    override fun gameDestroyed() {
        players.forEach {
            it.cleanup()
        }
        freezerMap.values.forEach {
            it.destroy()
        }
    }

    override fun registerEvents() = with(eventNode) {

        listenOnly<EntityTickEvent> {
            val player = entity as? Player ?: return@listenOnly

            leashHelperMap[player]?.teleport(player.position)

            if (player.heldPlayer != null) {
                val heldPlayer = player.heldPlayer!!
                val distance = player.getDistance(heldPlayer)

                if (distance > 5) {
                    heldPlayer.velocity = player.position.sub(heldPlayer.position).asVec().normalize().mul(15.0)
                }
                if (distance > 12) {
                    heldPlayer.teleport(player.position)
                }
            }
        }

        listenOnly<PlayerMoveEvent> {
            if (player.velocity.y > 0 && !player.isOnGround) {
                // On jump

                // Slow beast if they jump
                if (beastTeam.players.contains(player)) {
                    player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.08f
                    Manager.scheduler.buildTask { player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1f }
                        .delay(Duration.ofSeconds(1)).schedule()
                }
            }
        }

        listenOnly<EntityAttackEvent> {
            val player = entity as? Player ?: return@listenOnly
            val target = target as? Player ?: return@listenOnly

            if (beastTeam.players.contains(player) && !target.isKnocked) {
                target.damage(DamageType.fromPlayer(player), 0f)
                target.isKnocked = true
                target.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0f

                Manager.scheduler.buildTask {
                    target.isKnocked = false
                    // TODO: Stop holding player after cooldown
                    target.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1f
                }.delay(Duration.ofSeconds(30)).schedule()
            }
        }

        listenOnly<PlayerEntityInteractEvent> {
            val target = target as? Player ?: return@listenOnly

            if (target.isKnocked && hand == Player.Hand.MAIN && !leashHelperMap.contains(target)) {
                val leashHelper = Entity(EntityType.CHICKEN)
                val helperMeta = leashHelper.entityMeta
                helperMeta.isHasNoGravity = false
                helperMeta.isInvisible = true
                leashHelper.setInstance(instance, target.position)
                leashHelperMap[target] = leashHelper

                playSound(Sound.sound(SoundEvent.ENTITY_LEASH_KNOT_PLACE, Sound.Source.PLAYER, 1f, 1f), target.position)

                showLead(player, leashHelper)
                player.heldPlayer = target
            }

            if (frozenTeam.players.contains(target) && survivorsTeam.players.contains(player)) {
                val freezer = freezerMap.values.firstOrNull { it.heldPlayer == target } ?: return@listenOnly

                helpPlayer(freezer.heldPlayer, player, freezer)
            }
        }

        listenOnly<PlayerBlockInteractEvent> {
            if (player.heldPlayer != null && block == Block.LAPIS_BLOCK) {
                val heldPlayer = player.heldPlayer!!
                heldPlayer.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0f
                heldPlayer.teleport(blockPosition.add(0.5, 1.0, 0.5).asPos())

                leashHelperMap[heldPlayer]?.let { showLead(null, it) }
                leashHelperMap[heldPlayer]?.remove()
                leashHelperMap.remove(heldPlayer)
                player.heldPlayer = null

                val alivePlayerCount =
                    players.count { it.gameMode != GameMode.SPECTATOR && !freezerMap.containsKey(it) }
                if (alivePlayerCount == 0) {
                    victory(WinReason.BEAST_CAPTURED_ALL)
                }

                // Start freezing

                sendMessage(
                    Component.text()
                        .append(Component.text("❄", NamedTextColor.AQUA))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(heldPlayer.username, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" was captured by ", NamedTextColor.GRAY))
                        .append(Component.text(player.username, NamedTextColor.RED))
                )

                playSound(Sound.sound(SoundEvent.BLOCK_BEACON_ACTIVATE, Sound.Source.PLAYER, 100f, 2f), blockPosition)

                val bossBar = BossBar.bossBar(
                    Component.text(player.username, NamedTextColor.AQUA),
                    1f,
                    BossBar.Color.BLUE,
                    BossBar.Overlay.NOTCHED_10
                )

                val freezingTask = Manager.scheduler.buildTask {
                    if (beastTeam.players.any { it.getDistance(heldPlayer) < 10 }) {
                        bossBar.color(BossBar.Color.WHITE)
                        return@buildTask
                    }

                    if (heldPlayer.health < 2) {
                        freezerMap[heldPlayer]?.destroy()
                        sendMessage(
                            Component.text()
                                .append(Component.text("☠", NamedTextColor.RED))
                                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                                .append(Component.text(heldPlayer.username, NamedTextColor.AQUA, TextDecoration.BOLD))
                                .append(Component.text(" froze to death", NamedTextColor.GRAY))
                        )

                        heldPlayer.gameMode = GameMode.SPECTATOR
                    }

                    heldPlayer.health--
                    goRed(heldPlayer)
                    playSound(
                        Sound.sound(SoundEvent.ENTITY_PLAYER_HURT_FREEZE, Sound.Source.PLAYER, 10f, 1f),
                        player.position
                    )

                    bossBar.progress(heldPlayer.health / heldPlayer.maxHealth)

                    if (bossBar.progress() < 0.3) {
                        bossBar.color(BossBar.Color.RED)
                    } else {
                        bossBar.color(BossBar.Color.BLUE)
                    }
                }.delay(Duration.ofSeconds(3)).repeat(Duration.ofSeconds(3)).schedule()

                Freezer(this@BeastEscapeGame, heldPlayer, blockPosition, bossBar, freezingTask)
            }
        }

    }

    /**
     * Ran when a player is helped out of a freezer
     */
    fun helpPlayer(player: Player, helper: Player, freezer: Freezer) {
        sendMessage(
            Component.text()
                .append(Component.text("❄", NamedTextColor.AQUA))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(player.username, NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" was let out of the freezer by ", NamedTextColor.GRAY))
                .append(Component.text(helper.username, NamedTextColor.WHITE))
        )

        playSound(Sound.sound(SoundEvent.BLOCK_BEACON_DEACTIVATE, Sound.Source.PLAYER, 100f, 2f), freezer.position)

        freezer.destroy()
    }

    fun victory(winReason: WinReason) {
        val message = Component.text()
            .append(Component.text(winReason.description, NamedTextColor.RED))

        players.forEach {
            val victoryOrDefeatComponent =
                if ((winReason.beastWon && beastTeam.players.contains(it)) || (!winReason.beastWon && !beastTeam.players.contains(
                        it
                    ))
                ) {
                    Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD)
                } else {
                    Component.text("DEFEAT", NamedTextColor.RED, TextDecoration.BOLD)
                }

            it.showTitle(
                Title.title(
                    victoryOrDefeatComponent,
                    Component.text(winReason.description, NamedTextColor.GRAY),
                    Title.Times.of(
                        Duration.ZERO,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2)
                    )
                )
            )

            it.sendMessage(message)
        }
    }

    override fun instanceCreate(): Instance {
        val newInstance = Manager.instance.createInstanceContainer(
            Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        )

        newInstance.chunkGenerator = SuperflatGenerator

        return newInstance
    }

}
