package net.amathboi.mi84mod

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Mi84_calc : ModInitializer {
    private val logger = LoggerFactory.getLogger("mi84_calc")

	override fun onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		logger.info("Loading MI-84 Calculator")
	}
}