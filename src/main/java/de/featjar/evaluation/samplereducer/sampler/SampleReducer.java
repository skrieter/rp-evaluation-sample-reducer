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

import de.featjar.base.data.LexicographicIterator;
import de.featjar.formula.assignment.BooleanClause;
import de.featjar.formula.assignment.BooleanSolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

public class SampleReducer implements ISampleReducer {

    private void generateInteractions(
            List<BooleanSolution> configs,
            int[] variables,
            int index,
            LinkedHashMap<BooleanSolution, Double> fieldConfigurations,
            Collection<BooleanSolution> reducedSample,
            Map<Interaction, Interaction> synced) {
        int size = configs.size();
        if (size == 0) {
            return;
        }
        if (index == variables.length) {
            BooleanSolution booleanSolution = configs.get(0);
            if (size > 1) {
                int[] literals = new int[variables.length];
                for (int i = 0; i < literals.length; i++) {
                    literals[i] = booleanSolution.get(variables[i]);
                }
                Interaction interaction = new Interaction(literals);
                interaction.setCounter(size);
                synced.put(interaction, interaction);
            } else {
                synchronized (fieldConfigurations) {
                    if (fieldConfigurations.remove(booleanSolution) != null) {
                        reducedSample.add(booleanSolution);
                    }
                }
            }
            return;
        }
        List<BooleanSolution> left = new ArrayList<>(size);
        List<BooleanSolution> right = new ArrayList<>(size);
        int variable = variables[index];
        for (BooleanSolution config : configs) {
            if (config.get(variable) > 0) {
                left.add(config);
            } else {
                right.add(config);
            }
        }
        generateInteractions(left, variables, index + 1, fieldConfigurations, reducedSample, synced);
        generateInteractions(right, variables, index + 1, fieldConfigurations, reducedSample, synced);
    }

    /**
     * Method to reduce a given set of configuration to a sample covering the same
     * t-wise interactions
     *
     * @param sample the set of configuration that will be reduced
     * @param t
     * @return the reduced sample
     */
    public List<BooleanSolution> reduce(List<BooleanSolution> sample, int t) {
        if (sample.size() == 0) {
            return new ArrayList<>();
        }

        List<BooleanSolution> reducedSample = new ArrayList<>();
        LinkedHashMap<BooleanSolution, Double> fieldConfigurations = new LinkedHashMap<>();
        Double defaultValue = Double.valueOf(-1);
        sample.forEach(c -> fieldConfigurations.put(c, defaultValue));

        LinkedHashMap<Interaction, Interaction> finalInteractions = new LinkedHashMap<>();

        LexicographicIterator.stream(t, sample.get(0).size())
                .forEach(combination -> generateInteractions(
                        sample, combination.indexElements(), 0, fieldConfigurations, reducedSample, finalInteractions));

        for (Iterator<Entry<Interaction, Interaction>> iterator =
                        finalInteractions.entrySet().iterator();
                iterator.hasNext(); ) {
            Interaction interaction = iterator.next().getKey();
            if (reducedSample.stream().anyMatch(c -> c.containsAll(interaction))) {
                iterator.remove();
            }
        }

        // walk through all configs and give them a score and find the best scored
        // config
        for (Entry<BooleanSolution, Double> entry : fieldConfigurations.entrySet()) {
            BooleanSolution config = entry.getKey();
            double score = LexicographicIterator.parallelStream(t, config.size())
                    .mapToDouble(combination -> {
                        int[] select = combination.createSelection(config.get());
                        Interaction interaction = finalInteractions.get(new Interaction(select));
                        return interaction == null ? 0 : (1.0 / interaction.getCounter());
                    })
                    .sum();
            entry.setValue(score);
        }

        while (!finalInteractions.isEmpty()) {
            double bestScore = -1;
            BooleanSolution bestConfig = null;
            for (Iterator<Entry<BooleanSolution, Double>> iterator =
                            fieldConfigurations.entrySet().iterator();
                    iterator.hasNext(); ) {
                Entry<BooleanSolution, Double> configEntry = iterator.next();
                BooleanSolution config = configEntry.getKey();
                Double score = configEntry.getValue();
                if (score <= 0) {
                    iterator.remove();
                } else if (score > bestScore) {
                    bestScore = score;
                    bestConfig = config;
                }
            }
            if (bestConfig == null) {
                break;
            }

            // remove interactions that are now covered from the interactions that still
            // need
            // to be covered
            BooleanSolution bestConfig2 = bestConfig;
            List<Interaction> collect = finalInteractions.keySet().parallelStream()
                    .filter(interaction -> bestConfig2.containsAll(interaction))
                    .collect(Collectors.toList());
            if (collect.isEmpty()) {
                fieldConfigurations.remove(bestConfig);
            } else {
                // add best config to reduced sample and remove it from unchecked configs
                if (fieldConfigurations.remove(bestConfig) != null) {
                    reducedSample.add(bestConfig);
                }
                collect.stream().forEach(interaction -> {
                    for (Entry<BooleanSolution, Double> entry : fieldConfigurations.entrySet()) {
                        if (entry.getKey().containsAll(interaction)) {
                            entry.setValue(entry.getValue() - (1.0 / interaction.getCounter()));
                        }
                    }
                });
                finalInteractions.keySet().removeAll(collect);
            }
        }
        return reducedSample;
    }

    /**
     * Method to reduce a given set of configuration to a t-wise sample with a
     * random approach
     *
     * @param sample   the set of configuration that will be reduced
     * @param t
     * @return the reduced sample
     */
    public List<BooleanSolution> reduceRandom(List<BooleanSolution> sample, int t, long seed) {
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
        LexicographicIterator.stream(t, featureCount * 2).forEach(combination -> {
            int[] select = combination.getSelection(literals);
            if (!first.containsAll(select)) {
                for (int i = 0; i < select.length - 1; i++) {
                    int l = -select[i];
                    for (int j = i + 1; j < select.length; j++) {
                        if (l == select[j]) {
                            return;
                        }
                    }
                }
                if (fieldConfigurations.stream().anyMatch(c -> c.containsAll(select))) {
                    finalInteractions.add(new BooleanClause(Arrays.copyOf(select, select.length)));
                }
            }
        });

        while (!finalInteractions.isEmpty()) {
            BooleanSolution next = fieldConfigurations.remove(fieldConfigurations.size() - 1);
            reducedSample.add(next);
            finalInteractions.removeIf(interaction -> next.containsAll(interaction));
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
