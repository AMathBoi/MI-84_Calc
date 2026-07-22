package net.amathboi.mi84mod.client.calculator

/** Keeps the calculator position while inventory screens are opened and closed. */
object CalculatorPosition {
    private var savedX: Int? = null
    private var savedY: Int? = null

    fun xOrDefault(screenWidth: Int, calculatorWidth: Int, inventoryWidth: Int, gap: Int): Int {
        val defaultX = screenWidth / 2 - inventoryWidth / 2 - gap - calculatorWidth
        return savedX ?: defaultX.coerceAtLeast(0)
    }

    fun yOrDefault(screenHeight: Int, calculatorHeight: Int): Int {
        val defaultY = (screenHeight - calculatorHeight) / 2
        return savedY ?: defaultY.coerceAtLeast(0)
    }

    fun save(x: Int, y: Int) {
        savedX = x
        savedY = y
    }

    /** Restores default placement the next time a calculator position is requested. */
    fun reset() {
        savedX = null
        savedY = null
    }
}
