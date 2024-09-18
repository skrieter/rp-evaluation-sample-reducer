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
import de.featjar.formula.io.xml.XMLFeatureModelCNFFormulaFormat;
import de.featjar.formula.structure.IFormula;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * In this phase, we read and prepare the configurations for the evaluation and run the sampleReducer for the field configurations
 *
 * @author Rahel Sundermann
 * @author Sebastian Krieter
 */
public class ConvertIndustrySampleFilesPhase extends AConvertSampleFilesPhase {

    @Override
    protected List<Path> collectModelFiles() throws IOException {
        return Files.walk(modelPath.resolve(modelName))
                .filter(Files::isRegularFile)
                .filter(p -> "obfuscated.xml".equals(p.getFileName().toString()))
                .sorted(fileComparator)
                .collect(Collectors.toList());
    }

    @Override
    protected Result<IFormula> loadFormula(Path path) {
        return IO.load(path, new XMLFeatureModelCNFFormulaFormat());
    }

    @Override
    protected String getModelVersion(Path path) {
        return path.getName(path.getNameCount() - 2).toString();
    }

    @Override
    protected Pair<List<BooleanSolution>, VariableMap> loadFieldSample() throws IOException {
        VariableMap variables = new VariableMap();
        Path sampleDir = modelPath.resolve(modelName);
        List<BooleanSolution> sample = new ArrayList<>();
        Files.list(sampleDir)
                .filter(p -> p.getFileName().toString().endsWith(".csv"))
                .sorted(fileComparator)
                .forEach(p -> readVariablesFromSample(variables, p));
        Files.list(sampleDir)
                .filter(p -> p.getFileName().toString().endsWith(".csv"))
                .sorted(fileComparator)
                .forEach(p -> readSample(sample, variables, p));
        return new Pair<List<BooleanSolution>, VariableMap>(sample, variables);
    }

    private void readVariablesFromSample(VariableMap variables, Path p) {
        try {
            CSVFile.readAllLines(p, ";").skip(1).forEach(line -> {
                String first = line.get(1);
                addFeature(variables, first + "_" + line.get(2) + "_" + line.get(3));
                addFeature(variables, first + "_" + line.get(4) + "_" + line.get(5));
                addFeature(variables, first + "_" + line.get(6) + "_" + line.get(7));
            });
        } catch (IOException e) {
            FeatJAR.log().error(e);
            throw new RuntimeException(e);
        }
    }

    private void readSample(List<BooleanSolution> sample, VariableMap variables, Path p) {
        try {
            int[][] literals = new int[1][];
            literals[0] = null;
            CSVFile.readAllLines(p, ";").skip(1).forEach(line -> {
                String configID = line.get(0);
                if (configID != null && !configID.isBlank()) {
                    if (literals[0] != null) {
                        sample.add(new BooleanSolution(literals[0], false));
                    }
                    literals[0] = IntStream.rangeClosed(1, variables.getVariableCount())
                            .map(i -> -i)
                            .toArray();
                }
                String first = line.get(1);
                addVariable(variables, literals, first + "_" + line.get(2) + "_" + line.get(3));
                addVariable(variables, literals, first + "_" + line.get(4) + "_" + line.get(5));
                addVariable(variables, literals, first + "_" + line.get(6) + "_" + line.get(7));
            });
            if (literals[0] != null) {
                sample.add(new BooleanSolution(literals[0], false));
            }
        } catch (IOException e) {
            FeatJAR.log().error(e);
            throw new RuntimeException(e);
        }
    }

    private void addVariable(VariableMap variables, int[][] literals, String featureName) {
        int l = variables
                .get(featureName)
                .orElseThrow(p ->
                        new IllegalArgumentException(String.format("%s is not a valid variable name", featureName)));
        literals[0][l - 1] = l;
    }

    private void addFeature(VariableMap variables, String feature) {
        if (!variables.has(feature)) {
            variables.add(feature);
        }
    }
}
