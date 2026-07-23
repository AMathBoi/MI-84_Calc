package net.amathboi.mi84mod.client.calculator.controller

import net.amathboi.mi84mod.client.calculator.input.CalculatorKey
import net.amathboi.mi84mod.client.calculator.input.ModifierLayer

sealed interface DispatchResult {
    data object Handled : DispatchResult
    data class Placeholder(val key: CalculatorKey, val layer: ModifierLayer) : DispatchResult
    data class Unsupported(val key: CalculatorKey) : DispatchResult
}
