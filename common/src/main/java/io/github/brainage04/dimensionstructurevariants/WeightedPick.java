package io.github.brainage04.dimensionstructurevariants;

import it.unimi.dsi.fastutil.HashCommon;

/** Picks one of several weighted options from a seed alone, each as often as its weight says. */
public final class WeightedPick {
    private final int[] upTo;

    /** {@code weights} are positive; option {@code i} is picked with weight {@code weights[i]}. */
    public WeightedPick(int[] weights) {
        upTo = new int[weights.length];
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            total += weights[i];
            upTo[i] = total;
        }
    }

    /** The option a roll of {@code seed} lands on; the same seed always lands on the same one. */
    public int index(long seed) {
        int roll = (int) Math.floorMod(HashCommon.mix(seed), (long) upTo[upTo.length - 1]);
        for (int i = 0; i < upTo.length; i++) {
            if (roll < upTo[i]) return i;
        }
        return upTo.length - 1;
    }

    public int weight(int index) {
        return upTo[index] - (index == 0 ? 0 : upTo[index - 1]);
    }
}
