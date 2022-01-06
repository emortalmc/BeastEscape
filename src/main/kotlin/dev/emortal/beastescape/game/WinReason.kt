package dev.emortal.beastescape.game

enum class WinReason(val description: String, val beastWon: Boolean) {
    BEAST_CAPTURED_ALL("The beast captured all survivors", true),
    TIME_RAN_OUT("The survivors ran out of time", true),
    SURVIVORS_ESCAPED("The survivors escaped successfully", false),
    SURVIVORS_LEFT("The survivors left", true),
    BEAST_LEFT("The beast left", false)
}