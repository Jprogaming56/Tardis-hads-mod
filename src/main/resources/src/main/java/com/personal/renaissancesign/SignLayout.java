package com.personal.renaissancesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure layout maths for the sign lettering (no Minecraft classes), in "panel space":
 * x runs left to right as a reader sees it, y runs top to bottom, both in model units.
 */
final class SignLayout {
    private SignLayout() {}

    /** Dark inset panel size. */
    static final double PANEL_W = 30.4;
    static final double PANEL_H = 5.0;

    // Big lettering (POLICE, BOX): 4x7 font, voxels slightly taller than wide so the capitals look condensed.
    static final double BIG_VX = 0.30;
    static final double BIG_VY = 2.5 / 7.0;
    static final double BIG_GAP = 0.40;
    // Small lettering (PUBLIC / CALL): 3x5 font.
    static final double SMALL_V = 0.20;
    static final double SMALL_GAP = 0.20;
    static final double LINE_GAP = 0.30;
    static final double WORD_GAP = 2.0;

    record Rect(double x0, double y0, double x1, double y1) {}

    static List<Rect> rects() {
        double bigH = 7 * BIG_VY;
        double smallH = 5 * SMALL_V;

        double wPolice = wordWidth("POLICE", Glyphs.BIG, BIG_VX, BIG_GAP);
        double wPublic = wordWidth("PUBLIC", Glyphs.SMALL, SMALL_V, SMALL_GAP);
        double wCall = wordWidth("CALL", Glyphs.SMALL, SMALL_V, SMALL_GAP);
        double wBox = wordWidth("BOX", Glyphs.BIG, BIG_VX, BIG_GAP);
        double midW = Math.max(wPublic, wCall);

        double total = wPolice + WORD_GAP + midW + WORD_GAP + wBox;
        double x = (PANEL_W - total) / 2.0;

        List<Rect> out = new ArrayList<>();
        double bigTop = (PANEL_H - bigH) / 2.0;
        word(out, "POLICE", Glyphs.BIG, BIG_VX, BIG_VY, BIG_GAP, x, bigTop);
        x += wPolice + WORD_GAP;

        double smallBlock = smallH * 2 + LINE_GAP;
        double smallTop = (PANEL_H - smallBlock) / 2.0;
        word(out, "PUBLIC", Glyphs.SMALL, SMALL_V, SMALL_V, SMALL_GAP, x + (midW - wPublic) / 2.0, smallTop);
        word(out, "CALL", Glyphs.SMALL, SMALL_V, SMALL_V, SMALL_GAP, x + (midW - wCall) / 2.0,
                smallTop + smallH + LINE_GAP);
        x += midW + WORD_GAP;

        word(out, "BOX", Glyphs.BIG, BIG_VX, BIG_VY, BIG_GAP, x, bigTop);
        return out;
    }

    private static double wordWidth(String word, Map<Character, String[]> font, double vx, double gap) {
        double w = 0;
        for (int i = 0; i < word.length(); i++) {
            w += font.get(word.charAt(i))[0].length() * vx;
            if (i > 0) w += gap;
        }
        return w;
    }

    private static void word(List<Rect> out, String word, Map<Character, String[]> font, double vx, double vy,
            double gap, double x0, double y0) {
        double x = x0;
        for (int i = 0; i < word.length(); i++) {
            String[] g = font.get(word.charAt(i));
            glyph(out, g, vx, vy, x, y0);
            x += g[0].length() * vx + gap;
        }
    }

    /** Emits the lit voxels of one glyph as few rectangles as possible (horizontal runs, merged vertically). */
    private static void glyph(List<Rect> out, String[] g, double vx, double vy, double x0, double y0) {
        int rows = g.length;
        int cols = g[0].length();
        // runs[row] = list of {c0, c1} (c1 exclusive)
        List<int[]>[] runs = new List[rows];
        for (int r = 0; r < rows; r++) {
            runs[r] = new ArrayList<>();
            int c = 0;
            while (c < cols) {
                if (g[r].charAt(c) == '#') {
                    int s = c;
                    while (c < cols && g[r].charAt(c) == '#') c++;
                    runs[r].add(new int[] {s, c});
                } else {
                    c++;
                }
            }
        }
        boolean[][] used = new boolean[rows][];
        for (int r = 0; r < rows; r++) used[r] = new boolean[runs[r].size()];
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < runs[r].size(); i++) {
                if (used[r][i]) continue;
                int[] run = runs[r].get(i);
                int r2 = r + 1;
                while (r2 < rows) {
                    int match = -1;
                    for (int j = 0; j < runs[r2].size(); j++) {
                        int[] o = runs[r2].get(j);
                        if (!used[r2][j] && o[0] == run[0] && o[1] == run[1]) {
                            match = j;
                            break;
                        }
                    }
                    if (match < 0) break;
                    used[r2][match] = true;
                    r2++;
                }
                used[r][i] = true;
                out.add(new Rect(x0 + run[0] * vx, y0 + r * vy, x0 + run[1] * vx, y0 + r2 * vy));
            }
        }
    }
}
