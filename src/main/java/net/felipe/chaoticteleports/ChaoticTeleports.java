package net.felipe.chaoticteleports;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaoticTeleports implements ModInitializer {
    public static final String MOD_ID = "chaotic_teleports";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final TeleportManager teleportManager = new TeleportManager();

    @Override
    public void onInitialize() {
        teleportManager.initialize();
    }
}
