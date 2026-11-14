package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.List;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class WeightedPickTest {
    /**
     * The arithmetic of StructurePalettes.Choice.pick at fe1f6a7, returning the index it picks.
     * WeightedPick must agree with it, or a world generated again would get other blocks.
     */
    private static int previousPick(int[] weights, long seed) {
        int[] upTo = new int[weights.length];
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            total += weights[i];
            upTo[i] = total;
        }
        int roll = (int) Math.floorMod(HashCommon.mix(seed), (long) upTo[upTo.length - 1]);
        for (int i = 0; i < upTo.length; i++) {
            if (roll < upTo[i]) return i;
        }
        return weights.length - 1;
    }

    private static int[] array(List<Integer> weights) {
        return weights.stream().mapToInt(Integer::intValue).toArray();
    }

    @Property
    void picksWhatThePalettesPickedBefore(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 1, max = 1000) Integer> weights,
            @ForAll long seed) {
        int[] array = array(weights);
        assertEquals(previousPick(array, seed), new WeightedPick(array).index(seed));
    }

    @Property
    void picksAnOptionThatExists(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 1, max = 1000) Integer> weights,
            @ForAll long seed) {
        int index = new WeightedPick(array(weights)).index(seed);
        assertTrue(index >= 0 && index < weights.size(), "index " + index);
    }

    @Property
    void givesBackEachWeight(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 1, max = 1000) Integer> weights) {
        WeightedPick pick = new WeightedPick(array(weights));
        for (int i = 0; i < weights.size(); i++) {
            assertEquals(weights.get(i).intValue(), pick.weight(i));
        }
    }

    @Property
    void aSingleOptionIsAlwaysPicked(
            @ForAll @IntRange(min = 1, max = 1000) int weight, @ForAll long seed) {
        assertEquals(0, new WeightedPick(new int[] {weight}).index(seed));
    }

    /** The fortress_grass bricks (palettes.json) weigh 10, 3, 3 and 1. */
    @Example
    void picksEachOptionAsOftenAsItsWeight() {
        int[] weights = {10, 3, 3, 1};
        WeightedPick pick = new WeightedPick(weights);
        int[] counts = new int[weights.length];
        int seeds = 170_000;
        for (long seed = 0; seed < seeds; seed++) {
            counts[pick.index(seed)]++;
        }
        for (int i = 0; i < weights.length; i++) {
            double expected = seeds * weights[i] / 17.0D;
            assertTrue(
                    Math.abs(counts[i] - expected) < expected * 0.03D,
                    "option "
                            + i
                            + " was picked "
                            + counts[i]
                            + " times, expected about "
                            + expected);
        }
    }

    @Example
    void aSmallWeightIsPickedRarelyButNotNever() {
        WeightedPick pick = new WeightedPick(new int[] {1, 1000});
        int first = 0;
        for (long seed = 0; seed < 100_000; seed++) {
            if (pick.index(seed) == 0) first++;
        }
        assertTrue(first > 0 && first < 300, "the weight-1 option was picked " + first + " times");
    }
}
