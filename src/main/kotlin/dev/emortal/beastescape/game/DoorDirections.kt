package dev.emortal.beastescape.game

enum class DoorDirections(val otherDoorX: Int, val otherDoorZ: Int) {
    NORTH(1, 0),
    WEST(0, 1),
    SOUTH(1, 0),
    EAST(0, 1)
}