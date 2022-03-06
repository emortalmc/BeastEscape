package dev.emortal.beastescape.game

import dev.emortal.beastescape.util.goRed
import dev.emortal.beastescape.util.sendBlockDamage
import dev.emortal.beastescape.util.showLead
import dev.emortal.immortal.game.Game
import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.Team
import dev.emortal.immortal.util.MinestomRunnable
import dev.emortal.immortal.util.progressBar
import dev.emortal.immortal.util.reset
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.attribute.Attribute
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.other.AreaEffectCloudMeta
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.BlockChangePacket
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.utils.NamespaceID
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.item.item
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.chunksInRange
import world.cepi.kstom.util.playSound
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor
import kotlin.math.pow

class BeastEscapeGame(gameOptions: GameOptions) : Game(gameOptions) {
    override var spawnPosition: Pos = Pos(-8.0, -60.0, 25.0, -90f, 0f)

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

    private var timerTask: MinestomRunnable? = null

    val leashHelperMap = mutableMapOf<Player, Entity>()
    val freezerPosMap = mutableMapOf<Point, Freezer>()
    val freezerPlayerMap = mutableMapOf<Player, Freezer>()
    val computerMap = mutableMapOf<Point, Computer>()
    val computerHackerMap = mutableMapOf<Player, Computer>()
    var exitMap: MutableMap<Point, Exit> = mutableMapOf()

    var startTimestamp: Long = 0

    var computersToExit = 1
    var gameLength: Long = -1

    companion object {

        private val dateFormat = SimpleDateFormat("mm:ss")
    }

    var canHackDoors = false
    var hackedComputers = 0


    override fun playerJoin(player: Player) {

    }

    override fun playerLeave(player: Player) {
        freezerPlayerMap[player]?.destroy()
        player.cleanup()
    }

    override fun gameStarted() {

        exitMap[Pos(-33.5, -60.0, -3.0)] = Exit(this, Pos(-33.5, -60.0, -3.0))
        exitMap[Pos(14.5, -60.0, -32.0)] = Exit(this, Pos(14.5, -60.0, -32.0))

        computersToExit = players.size
        gameLength = ((computersToExit * 70) + 120) * 1000L

        instance.time = 18000

        scoreboard?.updateLineContent("infoLine", Component.text("Rolling...", NamedTextColor.GRAY))

        var picked = players.random()
        val offset = ThreadLocalRandom.current().nextInt(players.size)
        (0..15).forEach {
            Manager.scheduler.buildTask {
                picked.isGlowing = false
                picked = players.elementAt((it + offset) % players.size)
                picked.isGlowing = true

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
                    playSound(Sound.sound(SoundEvent.ENTITY_ENDER_DRAGON_GROWL, Sound.Source.HOSTILE, 1f, 1f))

                    beastTeam.add(picked)
                    players.forEach { plr ->
                        plr.reset()
                        if (plr == picked) return@forEach
                        survivorsTeam.add(plr)
                    }

                    Manager.scheduler.buildTask {
                        picked.isGlowing = false
                        picked.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.115f
                        setupGame()
                    }.delay(Duration.ofSeconds(2)).schedule()
                }
            }.delay(Duration.ofMillis((it * 4.0).pow(2.0).toLong())).schedule()
        }
    }

    fun setupGame() {
        instance.chunksInRange(Pos(0.0, 0.0, 0.0), 8).forEach {
            val chunk = instance.getChunk(it.first, it.second) ?: return@forEach

            for (x in 0 until 16) {
                for (y in -61 .. -58) {
                    for (z in 0 until 16) {
                        if (chunk.getBlock(x, y, z).compare(Block.CRIMSON_TRAPDOOR)) {
                            beastTeam.sendGroupedPacket(BlockChangePacket(Vec(x.toDouble() + (it.first * 16), y.toDouble(), z.toDouble() + (it.second * 16)), Block.AIR.stateId().toInt()))
                        }
                    }
                }
            }

        }

        scoreboard?.removeLine("infoLine")

        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "computersLeft",
                Component.text()
                    .append(Component.text("PCBs left: ", NamedTextColor.GRAY))
                    .append(Component.text(computersToExit, NamedTextColor.RED))
                    .build(),
                0
            )
        )

        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "timeLeft",
                Component.text()
                    .append(Component.text("Time left: ", NamedTextColor.YELLOW))
                    .append(Component.text(dateFormat.format(Date(gameLength)), NamedTextColor.RED))
                    .build(),
                1
            )
        )

        val survivorsMessage = Component.text()
            .append(Component.text("${" ".repeat(15)}YOU ARE A SURVIVOR", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("\n\nHack computers, open the gates and escape the beast!", NamedTextColor.GRAY))
            .append(Component.text("\nSave other survivors from bacta pods.", NamedTextColor.GRAY))
            .append(Component.text("\nThe beast can see if you fail to hack a computer", NamedTextColor.GRAY))

        val beastMessage = Component.text()
            .append(Component.text("${" ".repeat(15)}YOU ARE THE BEAST", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("\n\nCatch survivors and place them in a bacta pod!", NamedTextColor.GRAY))
            .append(Component.text("\nSurvivors can help other survivors out of a bacta pod.", NamedTextColor.GRAY))
            .append(Component.text("\nYou are notified of a player's location when they fail to hack a computer.", NamedTextColor.GRAY))

        survivorsTeam.sendMessage(survivorsMessage)

        beastTeam.players.forEach {

            it.addEffect(Potion(PotionEffect.BLINDNESS, 0, Short.MAX_VALUE.toInt()))

            val timeoutSeat = Entity(EntityType.AREA_EFFECT_CLOUD)
            val seatMeta = timeoutSeat.entityMeta as AreaEffectCloudMeta
            timeoutSeat.setNoGravity(true)
            seatMeta.radius = 0f
            timeoutSeat.setInstance(instance, spawnPosition.add(0.0, 20.0, 0.0)).thenRun {
                timeoutSeat.addPassenger(it)
            }

            it.sendMessage(beastMessage)
            it.inventory.setItemStack(0, item(Material.IRON_AXE))

            object : MinestomRunnable(timer = timer, repeat = Duration.ofMillis(150)) {
                override fun run() {
                    survivorsTeam.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM, Sound.Source.MASTER, 0.25f, if (currentIteration % 2 == 0) 0.5f else 1f), it.position)
                }
            }

            Manager.scheduler.buildTask {
                timeoutSeat.removePassenger(it)
                it.teleport(spawnPosition)
                it.removeEffect(PotionEffect.BLINDNESS)
            }
                .delay(Duration.ofSeconds(10))
                .schedule()
        }

        players.forEach {
            //it.addEffect(Potion(PotionEffect.BLINDNESS, 0, Short.MAX_VALUE.toInt()))
            it.teleport(spawnPosition)
            it.food = 0
        }

        startTimestamp = System.currentTimeMillis()
        timerTask = object : MinestomRunnable(repeat = Duration.ofSeconds(1), timer = timer) {

            override fun run() {
                val timeTaken = System.currentTimeMillis() - startTimestamp
                val timeLeft = gameLength - timeTaken

                scoreboard?.updateLineContent(
                    "timeLeft",
                    Component.text()
                        .append(Component.text("Time left: ", NamedTextColor.YELLOW))
                        .append(Component.text(dateFormat.format(Date(timeLeft)), NamedTextColor.GOLD))
                        .build()
                )

                if (timeLeft <= 500) {
                    victory(beastTeam)
                }
            }

        }
    }

    override fun gameDestroyed() {
        println("gameDestroyed()")
        val freezers = freezerPosMap.values.toList()
        println(freezers.size)
        freezers.forEach {
            it.destroy()
        }
        players.forEach {
            it.cleanup()
        }
    }

    override fun registerEvents() = with(eventNode) {

        //listenOnly<PlayerPacketEvent> {
        //    println(packet)
        //}

        val rand = ThreadLocalRandom.current()
        val maxDistance = 3*3
        listenOnly<PlayerTickEvent> {
            leashHelperMap[player]?.teleport(player.position)

            if (survivorsTeam.players.contains(player) && instance.getBlock(player.position).compare(Block.STRUCTURE_VOID)) {
                escape(player)
                return@listenOnly
            }

            if (survivorsTeam.players.contains(player) && canHackDoors) {
                val closestExit = exitMap.values.minByOrNull { it.position.distanceSquared(this.player.position) }

                if (closestExit != null && !closestExit.isOpen && closestExit.position.distanceSquared(player.position) < maxDistance) {
                    closestExit.hackedPercent += 1f / Exit.secondsToHack.toFloat() / 20f
                    player.showTitle(
                        Title.title(
                            Component.empty(),
                            Component.text()
                                .append(progressBar(closestExit.hackedPercent, 15, "-", NamedTextColor.GOLD, NamedTextColor.DARK_GRAY))
                                .build(),
                            Title.Times.of(Duration.ZERO, Duration.ofMillis(200), Duration.ofMillis(200))
                        )
                    )

                    if (player.aliveTicks % 25 == 0L) {
                        player.playSound(Sound.sound(SoundEvent.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, Sound.Source.MASTER, 0.75f, rand.nextFloat(0.8f, 1.25f)), closestExit.position)
                        closestExit.blocks.forEach {
                            sendBlockDamage(it, floor(closestExit.hackedPercent * 9).toInt().toByte())
                        }
                    }

                    if (closestExit.hackedPercent >= 1f) closestExit.open()
                }
            }


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
                    player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.06f
                    Manager.scheduler.buildTask { player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.115f }
                        .delay(Duration.ofSeconds(2)).schedule()
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
                    leashHelperMap[target]?.let { showLead(null, it) }
                    leashHelperMap[target]?.remove()
                    leashHelperMap.remove(target)
                    player.heldPlayer = null
                    target.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1f
                }.delay(Duration.ofSeconds(15)).schedule()
            }
        }

        listenOnly<PlayerEntityInteractEvent> {
            val target = target as? Player ?: return@listenOnly

            if (target.isKnocked && hand == Player.Hand.MAIN && !leashHelperMap.contains(target)) {
                val leashHelper = Entity(EntityType.CHICKEN)
                val helperMeta = leashHelper.entityMeta
                helperMeta.isHasNoGravity = false
                helperMeta.isSilent = true
                helperMeta.isInvisible = true
                leashHelper.setInstance(instance, target.position)
                leashHelperMap[target] = leashHelper

                playSound(Sound.sound(SoundEvent.ENTITY_LEASH_KNOT_PLACE, Sound.Source.PLAYER, 1f, 1f), target.position)

                showLead(player, leashHelper)
                player.heldPlayer = target
            }
        }

        listenOnly<InventoryCloseEvent> {
            val computer = computerHackerMap[player] ?: return@listenOnly
            val gui = computer.guiMap[player] ?: return@listenOnly
            if (gui.canFailFromExit) gui.fail()
            computer.removeHacker(player)
        }

        listenOnly<PlayerBlockInteractEvent> {
            if (hand != Player.Hand.MAIN) return@listenOnly

            if (survivorsTeam.players.contains(player) && block.compare(Block.COMMAND_BLOCK)) {
                val computer = computerMap[this.blockPosition] ?: Computer(this@BeastEscapeGame, blockPosition)
                if (computer.hackers.contains(player)) return@listenOnly
                if (computer.hackedPercent >= 1f) {
                    //player.sendActionBar(Component.text("This computer is already hacked!", NamedTextColor.RED))
                    return@listenOnly
                }
                computer.addHacker(player)
            }

            if (survivorsTeam.players.contains(player) && !frozenTeam.players.contains(player) && block == Block.LIGHT_BLUE_STAINED_GLASS) {
                val freezer = freezerPosMap[getCenterOfTank(blockPosition)!!] ?: return@listenOnly

                helpPlayer(freezer.heldPlayer, player, freezer)
            }

            if (player.heldPlayer != null && block == Block.LIGHT_BLUE_STAINED_GLASS) {
                val heldPlayer = player.heldPlayer!!
                heldPlayer.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1f

                val glassPos = getCenterOfTank(blockPosition) ?: return@listenOnly

                heldPlayer.teleport(glassPos)


                leashHelperMap[heldPlayer]?.let { showLead(null, it) }
                leashHelperMap[heldPlayer]?.remove()
                leashHelperMap.remove(heldPlayer)
                player.heldPlayer = null

                val alivePlayerCount = survivorsTeam.players.count { !frozenTeam.players.contains(it) }
                if (alivePlayerCount - 1 == 0) {
                    sendMessage(
                        Component.text()
                            .append(Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                            .append(Component.text("The beast captured all the survivors", NamedTextColor.GRAY))
                    )
                    victory(beastTeam)
                }

                // Start freezing
                sendMessage(
                    Component.text()
                        .append(Component.text("❄", NamedTextColor.AQUA))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(heldPlayer.username, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" was captured in a bacta pod by ", NamedTextColor.GRAY))
                        .append(Component.text(player.username, NamedTextColor.RED))
                )

                playSound(Sound.sound(SoundEvent.BLOCK_BEACON_ACTIVATE, Sound.Source.PLAYER, 100f, 2f), blockPosition)

                val bossBar = BossBar.bossBar(
                    Component.text(heldPlayer.username, NamedTextColor.AQUA),
                    1f,
                    BossBar.Color.BLUE,
                    BossBar.Overlay.NOTCHED_10
                )

                val freezingTask = Manager.scheduler.buildTask {
                    if (beastTeam.players.any { it.position.distanceSquared(heldPlayer.position) < 10 * 10 }) {
                        bossBar.color(BossBar.Color.WHITE)
                        return@buildTask
                    }

                    if (heldPlayer.health < 2) {
                        freezerPlayerMap[heldPlayer]?.destroy()
                        sendMessage(
                            Component.text()
                                .append(Component.text("☠", NamedTextColor.RED))
                                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                                .append(Component.text(heldPlayer.username, NamedTextColor.AQUA, TextDecoration.BOLD))
                                .append(Component.text(" drowned", NamedTextColor.GRAY))
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

                Freezer(this@BeastEscapeGame, heldPlayer, glassPos, bossBar, freezingTask)
            }
        }

    }

    private fun escape(player: Player) {
        player.isInvisible = true
        player.gameMode = GameMode.SPECTATOR
        player.isAllowFlying = true
        player.isFlying = true
        player.clearEffects()
        survivorsTeam.remove(player)

        showTitle(
            Title.title(
                Component.text(player.username, NamedTextColor.GREEN),
                Component.text("has escaped!", NamedTextColor.GREEN),
                Title.Times.of(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(2))
            )
        )
        sendMessage(Component.text("${player.username} has escaped!", NamedTextColor.GREEN))

        val playersLeft = players.filter { survivorsTeam.players.contains(it) && !frozenTeam.players.contains(it) && !beastTeam.players.contains(it) }
        if (playersLeft.isEmpty()) {
            victory(players.filter { !frozenTeam.players.contains(it) && !beastTeam.players.contains(it) })
        }
    }

    private fun getCenterOfTank(blockPosition: Point): Pos? {
        var glassPos: Pos? = null
        for (x in -1..1) {
            for (z in -1..1) {
                val pos = blockPosition.add(x.toDouble(), 0.0, z.toDouble())
                if (instance.getBlock(pos).compare(Block.LIGHT_BLUE_STAINED_GLASS)) {
                    for (y in 0 downTo -4) {
                        val newPos = pos.add(0.0, y.toDouble(), 0.0)
                        if (instance.getBlock(newPos).compare(Block.IRON_BLOCK)) {
                            glassPos = newPos.asPos().add(0.0, 0.5, 0.0)
                            break
                        }
                    }
                }
            }
        }
        return glassPos
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
                .append(Component.text(" was let out of the bacta pod by ", NamedTextColor.GRAY))
                .append(Component.text(helper.username, NamedTextColor.WHITE))
        )

        playSound(Sound.sound(SoundEvent.BLOCK_BEACON_DEACTIVATE, Sound.Source.PLAYER, 100f, 2f), freezer.position)

        freezer.destroy()
        player.teleport(helper.position)
        player.isKnocked = false
        player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1f
        player.velocity = Vec(0.0, 10.0, 0.0)
        player.askSynchronization()
    }

    override fun gameWon(winningPlayers: Collection<Player>) {
        timerTask?.cancel()
    }

    override fun instanceCreate(): Instance {
        val newInstance = Manager.instance.createInstanceContainer(
            Manager.dimensionType.getDimension(NamespaceID.from("ftf"))!!
        )

        newInstance.chunkLoader = AnvilLoader("./maps/beastescape/ftf")
        //newInstance.chunkGenerator = SuperflatGenerator

        return newInstance
    }

}
