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

import de.featjar.formula.assignment.BooleanSolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SetCoverSampleReducer implements ISampleReducer {

    private BooleanSolution[] fieldConfigurations;
    private int n, t, t2;

    private LinkedHashSet<BooleanSolution> reducedSample;
    private LinkedHashSet<Interaction> interactions;
    private int[][] indices;

    private void generate(int first) {
        int[] elementIndices;
        boolean[] marker;
        elementIndices = new int[t];
        marker = new boolean[t];
        for (int i = 0; i < t - 1; i++) {
            elementIndices[i] = i;
        }
        elementIndices[t - 1] = first;

        int[][] curIndices = new int[t][];
        int[] searchIndex = new int[t];

        int i = 0;
        for (; ; ) {
            int[] literals = new int[t];
            for (int k2 = 0; k2 < literals.length; k2++) {
                int var = elementIndices[k2] + 1;
                int l = marker[k2] ? -var : var;
                literals[k2] = l;
                curIndices[k2] = indices[l + n];
                searchIndex[k2] = 0;
            }
            Arrays.sort(curIndices, (a, a2) -> a.length - a2.length);
            int counter = 0;
            int configIndex = -1;
            int[] firstInd = curIndices[0];
            firstLoop:
            for (int j = 0; j < firstInd.length; j++) {
                int index = firstInd[j];
                for (int k = 1; k < t; k++) {
                    int[] curIndex = curIndices[k];
                    if (searchIndex[k] >= curIndex.length) {
                        break firstLoop;
                    }
                    int binarySearch = Arrays.binarySearch(curIndex, searchIndex[k], curIndex.length, index);
                    if (binarySearch < 0) {
                        searchIndex[k] = -binarySearch;
                        continue firstLoop;
                    } else {
                        searchIndex[k] = binarySearch + 1;
                    }
                }
                configIndex = index;
                counter++;
            }
            if (counter > 1) {
                Interaction interaction = new Interaction(literals);
                interaction.setCounter(counter);
                synchronized (interactions) {
                    interactions.add(interaction);
                }
            } else if (counter == 1) {
                BooleanSolution config = fieldConfigurations[configIndex];
                synchronized (reducedSample) {
                    reducedSample.add(config);
                }
            }

            for (i = 0; i < t2; i++) {
                int index = elementIndices[i];
                if (index + 1 < elementIndices[i + 1]) {
                    if (marker[i]) {
                        elementIndices[i] = index + 1;
                    }
                    marker[i] = !marker[i];
                    for (int j = i - 1; j >= 0; j--) {
                        elementIndices[j] = j;
                        marker[j] = false;
                    }
                    break;
                } else {
                    if (marker[i]) {
                        marker[i] = false;
                        continue;
                    } else {
                        marker[i] = true;
                        for (int j = i - 1; j >= 0; j--) {
                            elementIndices[j] = j;
                            marker[j] = false;
                        }
                        break;
                    }
                }
            }
            if (i == t2) {
                if (marker[i]) {
                    break;
                } else {
                    marker[i] = true;
                    for (int j = i - 1; j >= 0; j--) {
                        elementIndices[j] = j;
                        marker[j] = false;
                    }
                }
            }
        }
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
        n = sample.get(0).size();

        if (t > n) {
            throw new IllegalArgumentException(String.format("%d > %d", t, n));
        }
        this.t = t;
        t2 = t - 1;
        fieldConfigurations = sample.toArray(new BooleanSolution[0]);
        reducedSample = new LinkedHashSet<>();
        interactions = new LinkedHashSet<>();

        indices = new int[2 * n + 1][];
        int[] negIndices = new int[fieldConfigurations.length];
        int[] posIndices = new int[fieldConfigurations.length];
        for (int j = 1; j <= n; j++) {
            int k1 = 0, k2 = 0;
            for (int i = 0; i < fieldConfigurations.length; i++) {
                BooleanSolution config = fieldConfigurations[i];
                if (config.get(j - 1) < 0) {
                    negIndices[k1++] = i;
                } else {
                    posIndices[k2++] = i;
                }
            }
            indices[n - j] = Arrays.copyOf(negIndices, k1);
            indices[j + n] = Arrays.copyOf(posIndices, k2);
        }

        IntStream.range(t - 1, n).parallel().forEach(this::generate);

        long[] interactionCountPerConfiguration = new long[sample.size()];
        int fieldConfigurationsSize = sample.size();
        for (int j = 0; j < fieldConfigurations.length; j++) {
            BooleanSolution config = fieldConfigurations[j];
            if (reducedSample.contains(config)) {
                fieldConfigurations[j] = null;
            }
        }

        List<Interaction> collect = interactions.parallelStream()
                .filter(interaction -> {
                    if (reducedSample.parallelStream().anyMatch(c -> c.containsAll(interaction))) {
                        return true;
                    } else {
                        int[][] curIndices = new int[t][];
                        int[] searchIndex = new int[t];
                        int[] is = interaction.get();
                        for (int k2 = 0; k2 < is.length; k2++) {
                            curIndices[k2] = indices[is[k2] + n];
                            searchIndex[k2] = 0;
                        }

                        Arrays.sort(curIndices, (a, a2) -> a.length - a2.length);
                        int[] firstInd = curIndices[0];
                        firstLoop:
                        for (int j = 0; j < firstInd.length; j++) {
                            int index = firstInd[j];
                            if (fieldConfigurations[index] != null) {
                                for (int k = 1; k < t; k++) {
                                    int[] curIndex = curIndices[k];
                                    if (searchIndex[k] >= curIndex.length) {
                                        break firstLoop;
                                    }
                                    int binarySearch =
                                            Arrays.binarySearch(curIndex, searchIndex[k], curIndex.length, index);
                                    if (binarySearch < 0) {
                                        searchIndex[k] = -binarySearch;
                                        continue firstLoop;
                                    } else {
                                        searchIndex[k] = binarySearch + 1;
                                    }
                                }
                                interactionCountPerConfiguration[index]++;
                            }
                        }
                        return false;
                    }
                })
                .collect(Collectors.toList());
        interactions.removeAll(collect);

        while (!interactions.isEmpty()) {
            double mostConfigs = -1;
            int bestConfigIndex = -1;
            for (int j = 0; j < fieldConfigurationsSize; j++) {
                double configCount = interactionCountPerConfiguration[j];
                if (fieldConfigurations[j] != null) {
                    if (configCount == 0) {
                        fieldConfigurations[j] = null;
                    } else if (configCount > mostConfigs) {
                    	mostConfigs = configCount;
                        bestConfigIndex = j;
                    }
                }
            }
            if (bestConfigIndex < 0) {
                break;
            }

            BooleanSolution bestConfig = fieldConfigurations[bestConfigIndex];
            reducedSample.add(bestConfig);
            fieldConfigurations[bestConfigIndex] = null;

            List<Interaction> collect2 = interactions.parallelStream()
                    .filter(interaction -> bestConfig.containsAll(interaction))
                    .peek(interaction -> {
                        int[][] curIndices = new int[t][];
                        int[] searchIndex = new int[t];
                        int[] is = interaction.get();
                        for (int k2 = 0; k2 < is.length; k2++) {
                            curIndices[k2] = indices[is[k2] + n];
                            searchIndex[k2] = 0;
                        }

                        Arrays.sort(curIndices, (a, a2) -> a.length - a2.length);
                        int[] firstInd = curIndices[0];
                        firstLoop:
                        for (int j = 0; j < firstInd.length; j++) {
                            int index = firstInd[j];
                            BooleanSolution booleanSolution = fieldConfigurations[index];
                            if (booleanSolution != null) {
                                for (int k = 1; k < t; k++) {
                                    int[] curIndex = curIndices[k];
                                    if (searchIndex[k] >= curIndex.length) {
                                        break firstLoop;
                                    }
                                    int binarySearch =
                                            Arrays.binarySearch(curIndex, searchIndex[k], curIndex.length, index);
                                    if (binarySearch < 0) {
                                        searchIndex[k] = -binarySearch;
                                        continue firstLoop;
                                    } else {
                                        searchIndex[k] = binarySearch + 1;
                                    }
                                }
                                synchronized (booleanSolution) {
                                    interactionCountPerConfiguration[index]--;
                                }
                            }
                        }
                    })
                    .collect(Collectors.toList());

            interactions.removeAll(collect2);
        }
        return new ArrayList<>(reducedSample);
    }
}
