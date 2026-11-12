package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A second panel under the recording caption, in its style: every biome the shot's palette covers
 * and whether the palette is on. The caption panel (MinecraftModConventions' {@code
 * ClientGameTestRecordingHud}) draws one line per field and cuts a line at 360 pixels, which a list
 * of 28 biome names overruns, so the list wraps here instead.
 */
final class StageCaptionPanel {
    /** The caption panel's own layout, which this panel continues below it. */
    private static final int SCREEN_MARGIN = 5;

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int CAPTION_LINES = 3;
    private static final int MAX_WIDTH = 360;
    private static final int BACKGROUND = 0xA0000000;
    private static final int LIST_COLOR = 0xFF90EE90;
    private static final int OFF_COLOR = 0xFFAAAAAA;
    private static final int ON_COLOR = 0xFFFFFFFF;

    private static volatile List<String> biomes = List.of();
    private static volatile String heading = null;
    private static volatile String status = null;
    private static volatile boolean on;
    private static boolean registered;

    private StageCaptionPanel() {}

    static void register() {
        if (registered) return;
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.SCOREBOARD,
                DimensionStructureVariants.of("stage_caption_panel"),
                StageCaptionPanel::render);
        registered = true;
    }

    /** Shows the panel for a shot: its biomes (none when it stands for one) and the off status. */
    static void show(String biomeHeading, List<String> biomeNames, String offStatus) {
        heading = biomeHeading;
        biomes = List.copyOf(biomeNames);
        status = offStatus;
        on = false;
    }

    static void switchOn(String onStatus) {
        status = onStatus;
        on = true;
    }

    static void clear() {
        heading = null;
        biomes = List.of();
        status = null;
    }

    private static void render(
            GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        String line = status;
        if (line == null) return;
        Font font = Minecraft.getInstance().font;
        List<String> rows = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (heading != null) {
            rows.add(heading);
            colors.add(LIST_COLOR);
            for (String row : wrap(font, String.join(", ", biomes))) {
                rows.add(row);
                colors.add(LIST_COLOR);
            }
        }
        rows.add(line);
        colors.add(on ? ON_COLOR : OFF_COLOR);
        int width = 0;
        for (String row : rows) width = Math.max(width, font.width(row));
        int panelWidth = width + PADDING * 2;
        int panelHeight = rows.size() * LINE_HEIGHT + PADDING * 2 - 2;
        int x =
                Minecraft.getInstance().getWindow().getGuiScaledWidth()
                        - panelWidth
                        - SCREEN_MARGIN;
        int y = SCREEN_MARGIN + CAPTION_LINES * LINE_HEIGHT + PADDING * 2 - 2 + 2;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, BACKGROUND);
        for (int i = 0; i < rows.size(); i++) {
            graphics.text(
                    font,
                    rows.get(i),
                    x + PADDING,
                    y + PADDING + i * LINE_HEIGHT,
                    colors.get(i),
                    true);
        }
    }

    /** Breaks the list after a comma so no row is wider than the caption panel's widest line. */
    private static List<String> wrap(Font font, String text) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = row.isEmpty() ? word : row + " " + word;
            if (!row.isEmpty() && font.width(candidate) > MAX_WIDTH) {
                rows.add(row.toString());
                row = new StringBuilder(word);
            } else {
                row = new StringBuilder(candidate);
            }
        }
        if (!row.isEmpty()) rows.add(row.toString());
        return rows;
    }
}
