package dev.emortal.beastescape.game

import dev.emortal.beastescape.util.breakBlock
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent
import world.cepi.kstom.util.playSound

class Exit(
    val game: BeastEscapeGame,
    val position: Point
) {

    val blocks = Array<Point>(4) { Pos.ZERO }

    companion object {
        val secondsToHack = 15
    }

    var hackedPercent = 0f
    var isOpen = false

    init {
        val blockPos = Pos(position.blockX().toDouble(), position.blockY().toDouble(), position.blockZ().toDouble())
        val initialBlock = game.instance.getBlock(blockPos)
        val hinge = initialBlock.getProperty("hinge")
        val facing = initialBlock.getProperty("facing")
        val hingeIsLeft = hinge == "left"
        val doorDir = DoorDirections.valueOf(facing.uppercase())
        val doorX = doorDir.otherDoorX + 1
        val doorZ = doorDir.otherDoorZ + 1

        var doorBlocks = 0
        for (x in -doorX until doorX) {
            for (y in 0 until 2) {
                for (z in -doorZ until doorZ) {
                    val newPos = blockPos.add(
                        (if (hingeIsLeft) -x else x).toDouble(),
                        y.toDouble(),
                        (if (hingeIsLeft) -z else z).toDouble()
                    )

                    val block = game.instance.getBlock(newPos)
                    if (block.compare(Block.IRON_DOOR)) {
                        //val hinge = block.getProperty("hinge")
                        //val facing = block.getProperty("facing")
                        //val half = block.getProperty("half")

                        //println("modified block at ${newPos.toString()}")
                        //game.instance.setBlock(newPos, Block.IRON_DOOR.withProperties(mapOf("facing" to facing, "hinge" to hinge, "half" to half, "open" to "true")))

                        blocks[doorBlocks++] = newPos
                    }


                }
            }
        }
    }

    fun open() {
        if (isOpen) return
        isOpen = true

        game.instance.playSound(Sound.sound(SoundEvent.BLOCK_IRON_TRAPDOOR_CLOSE, Sound.Source.MASTER, 2f, 0.8f), position)
        game.instance.playSound(Sound.sound(SoundEvent.BLOCK_ANCIENT_DEBRIS_BREAK, Sound.Source.MASTER, 2f, 0.8f), position)

        blocks.forEach {
            game.breakBlock(it, Block.IRON_DOOR)
            game.instance.setBlock(it, Block.AIR)
        }

    }

}
