/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-evaluation-sample-reducer.
 *
 * evaluation-sample-reducer is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * evaluation-sample-reducer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with evaluation-sample-reducer. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatJAR> for further information.
 */
package de.featjar.evaluation.samplereducer.sampler;

import de.featjar.base.data.Ints;
import de.featjar.base.data.LexicographicIterator;
import de.featjar.formula.assignment.BooleanClause;
import de.featjar.formula.assignment.BooleanSolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

public class RandomSampleReducer implements ISampleReducer {

    private final long seed;

    public RandomSampleReducer(long seed) {
        this.seed = seed;
    }

    /**
     * Method to reduce a given set of configuration to a t-wise sample with a
     * random approach
     *
     * @param sample the set of configuration that will be reduced
     * @param t
     * @return the reduced sample
     */
    public List<BooleanSolution> reduce(List<BooleanSolution> sample, int t) {
        List<BooleanSolution> reducedSample = new ArrayList<>();
        if (sample.size() == 0) {
            return reducedSample;
        }

        ArrayList<BooleanSolution> fieldConfigurations = new ArrayList<>(sample);
        Collections.shuffle(fieldConfigurations, new Random(seed));

        BooleanSolution first = fieldConfigurations.remove(fieldConfigurations.size() - 1);
        reducedSample.add(first);
        LinkedHashSet<BooleanClause> finalInteractions = new LinkedHashSet<>();
        int featureCount = sample.get(0).size();
        int[] literals = getLiterals(featureCount);
        int[] grayCode = Ints.grayCode(t);
        LexicographicIterator.stream(t, featureCount).forEach(combination -> {
            int[] select = combination.getSelection(literals);
            if (!first.containsAll(select)) {
                for (int g : grayCode) {
                    if (fieldConfigurations.parallelStream().anyMatch(c -> c.containsAll(select))) {
                        finalInteractions.add(new BooleanClause(Arrays.copyOf(select, select.length)));
                    }
                    select[g] = -select[g];
                }
            }
        });

        while (!finalInteractions.isEmpty()) {
            BooleanSolution nextConfig = fieldConfigurations.remove(fieldConfigurations.size() - 1);
            reducedSample.add(nextConfig);
            finalInteractions.removeIf(interaction -> nextConfig.containsAll(interaction));
        }
        return reducedSample;
    }

    private int[] getLiterals(int featureCount) {
        int[] literals = new int[featureCount * 2];
        for (int i = 0; i < featureCount; i++) {
            int literal = i + 1;
            literals[i] = -literal;
            literals[i + featureCount] = literal;
        }
        return literals;
    }
}
