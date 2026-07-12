package com.saolghra.armor_hud.fabric.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Client GameTest that drives the real Minecraft client to visually verify the Armor HUD.
 * Runs via {@code ./gradlew :fabric:<version>:runClientGameTest} (Fabric 1.21.5+ only, where the
 * client-gametest API exists). Screenshots land in {@code build/run/clientGameTest/screenshots} and
 * can be reviewed or diffed against committed golden images.
 *
 * <p>The equipped armor uses a spread of damage values so a single frame exercises the durability
 * colour gradient (green -> yellow -> red) and the low-durability warning icon.
 */
@SuppressWarnings("UnstableApiUsage")
public class ArmorHudClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            // Baseline: no armor -> HUD hides all four (empty) slots.
            context.takeScreenshot("armor_hud-empty");

            // Equip a full set with a spread of damage: helmet healthy (green), chest ~half
            // (yellow), leggings + boots low (red + bobbing warning icon).
            TestServerContext server = singleplayer.getServer();
            server.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet[minecraft:damage=40]");
            server.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate[minecraft:damage=260]");
            server.runCommand("item replace entity @p armor.legs with minecraft:diamond_leggings[minecraft:damage=470]");
            server.runCommand("item replace entity @p armor.feet with minecraft:diamond_boots[minecraft:damage=390]");
            context.waitTicks(20);

            // HUD visible with durability bars + low-durability warning.
            context.takeScreenshot("armor_hud-equipped");
        }
    }
}
