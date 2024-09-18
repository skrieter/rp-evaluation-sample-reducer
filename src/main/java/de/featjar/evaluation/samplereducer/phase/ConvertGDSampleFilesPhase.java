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
package de.featjar.evaluation.samplereducer.phase;

import de.featjar.base.FeatJAR;
import de.featjar.base.data.Pair;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.io.dimacs.FormulaDimacsFormat;
import de.featjar.formula.structure.IFormula;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * In this phase, we read and prepare the configurations for the evaluation and run the sampleReducer for the field configurations
 *
 * @author Rahel Sundermann
 * @author Sebastian Krieter
 */
public class ConvertGDSampleFilesPhase extends AConvertSampleFilesPhase {

    private VariableMap combinedVariableMap;

    @Override
    protected List<Path> collectModelFiles() throws IOException {
        combinedVariableMap = new VariableMap();
        return Files.walk(modelPath.resolve(modelName))
                .filter(Files::isRegularFile)
                .filter(p -> "model.dimacs".equals(p.getFileName().toString()))
                .sorted(fileComparator)
                .collect(Collectors.toList());
    }

    @Override
    protected Result<IFormula> loadFormula(Path path) {
        Result<IFormula> load = IO.load(path, new FormulaDimacsFormat());
        if (load.isPresent()) {
            combinedVariableMap.addAll(VariableMap.of(load.get()));
        }
        return load;
    }

    @Override
    protected String getModelVersion(Path path) {
        return "0";
    }

    @Override
    protected Pair<List<BooleanSolution>, VariableMap> loadFieldSample() throws IOException {
        List<BooleanSolution> sample = readSample(
                modelVersionName,
                combinedVariableMap,
                modelPath.resolve(modelName).resolve("configurations.csv"));
        return new Pair<List<BooleanSolution>, VariableMap>(sample, combinedVariableMap);
    }

    private List<BooleanSolution> readSample(String modelVersionName, VariableMap variables, Path p) {
        try {
            LinkedHashMap<String, List<String>> configMap = new LinkedHashMap<>();
            CSVFile.readAllLines(p, ";").forEach(l -> {
                String configID = l.get(0);
                String featureName = l.get(1);
                List<String> list = configMap.get(configID);
                if (list == null) {
                    list = new ArrayList<>();
                    configMap.put(configID, list);
                }
                list.add(featureName);
            });
            return configMap.entrySet().stream()
                    .map(e -> readConfig(variables, e.getValue()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            FeatJAR.log().error(e);
            throw new RuntimeException(e);
        }
    }

    private BooleanSolution readConfig(VariableMap variables, List<String> featureNames) {
        try {
            int[] literals = IntStream.rangeClosed(1, variables.getVariableCount())
                    .map(i -> -i)
                    .toArray();
            featureNames.stream()
                    .mapToInt(v -> variables
                            .get(v)
                            .orElseThrow(p ->
                                    new IllegalArgumentException(String.format("%s is not a valid variable name", v))))
                    .forEach(l -> literals[l - 1] = l);
            return new BooleanSolution(literals);
        } catch (IllegalArgumentException e) {
            FeatJAR.log().error(e.getMessage());
            return null;
        } catch (Exception e) {
            FeatJAR.log().error(e);
            return null;
        }
    }
}
