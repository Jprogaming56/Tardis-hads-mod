package com.personal.renaissancesign;

import java.util.Map;

/** Tiny pixel fonts for the sign. '#' is a lit voxel. Rows run top to bottom. */
final class Glyphs {
    private Glyphs() {}

    /** Tall condensed capitals (4x7, I is 3 wide) for POLICE and BOX, shaped like the lettering on the show prop. */
    static final Map<Character, String[]> BIG = Map.of(
            'P', new String[] {"###.", "#..#", "#..#", "###.", "#...", "#...", "#..."},
            'O', new String[] {".##.", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."},
            'L', new String[] {"#...", "#...", "#...", "#...", "#...", "#...", "####"},
            'I', new String[] {"###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"},
            'C', new String[] {".###", "#...", "#...", "#...", "#...", "#...", ".###"},
            'E', new String[] {"####", "#...", "#...", "###.", "#...", "#...", "####"},
            'B', new String[] {"###.", "#..#", "#..#", "###.", "#..#", "#..#", "###."},
            'X', new String[] {"#..#", "#..#", ".##.", ".##.", ".##.", "#..#", "#..#"});

    /** Small capitals (3x5) for the two stacked lines PUBLIC / CALL. */
    static final Map<Character, String[]> SMALL = Map.of(
            'P', new String[] {"###", "#.#", "###", "#..", "#.."},
            'U', new String[] {"#.#", "#.#", "#.#", "#.#", "###"},
            'B', new String[] {"##.", "#.#", "##.", "#.#", "##."},
            'L', new String[] {"#..", "#..", "#..", "#..", "###"},
            'I', new String[] {"###", ".#.", ".#.", ".#.", "###"},
            'C', new String[] {"###", "#..", "#..", "#..", "###"},
            'A', new String[] {".#.", "#.#", "###", "#.#", "#.#"});
}
