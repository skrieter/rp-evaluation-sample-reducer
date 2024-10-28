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
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.base.log.Log.Verbosity;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.ABooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.io.binary.BooleanAssignmentGroupsCompressedFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.IntStream;

/**
 * In this phase, we run YASA on the read feature model
 *
 * @author Sebastian Krieter
 */
public class CreateCombinedSamplePhase extends ACreateSamplePhase {

    private LinkedHashMap<Integer, List<String>> yasaSamples;
    private LinkedHashMap<Integer, String> fieldSamples;

    @Override
    protected void initEval() {
        yasaSamples = new LinkedHashMap<>();
        fieldSamples = new LinkedHashMap<>();
        try {
            CSVFile.readAllLines(dataPath.resolve("samples.csv")).skip(1).forEach(l -> {
                int systemID = Integer.parseInt(l.get(1));
                String type = l.get(4);
                String path = genPath.resolve(systemNames.get(systemID))
                        .resolve(l.get(3))
                        .toString();
                if (type.equals("f")) {
                    fieldSamples.put(systemID, path);
                } else if (type.equals("t")) {
                    List<String> list = yasaSamples.get(systemID);
                    if (list == null) {
                        list = new ArrayList<>(2);
                        yasaSamples.put(systemID, list);
                    }
                    list.add(path);
                }
            });
        } catch (Exception e) {
            FeatJAR.log().error("Could not read samples.csv.");
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void runOptionLoop() {
        optionCombiner.init(systemsOption);
        optionCombiner.loopOverOptions(this::optionLoop);
    }

    @Override
    protected void setOptions(int lastChanged) {
        switch (lastChanged) {
            case 0:
                modelName = optionCombiner.getValue(0);
        }
    }

    @Override
    protected int getTaskCount() {
        return yasaSamples.get(getSystemId(modelName)).size();
    }

    @Override
    protected void processFile(Path modelDir) {
        int systemID = getSystemId(modelName);
        Path fieldSamplePath = Paths.get(fieldSamples.get(systemID));

        Result<BooleanAssignmentGroups> fieldSampleResult =
                IO.load(fieldSamplePath, new BooleanAssignmentGroupsCompressedFormat());
        if (fieldSampleResult.isEmpty()) {
            FeatJAR.log().warning("Could not read file %s", fieldSamplePath);
            FeatJAR.log().problems(fieldSampleResult.getProblems(), Verbosity.WARNING);
            return;
        }
        List<? extends ABooleanAssignment> fieldSample =
                fieldSampleResult.get().getGroups().get(0);
        VariableMap fieldSampleVariableMap = fieldSampleResult.get().getVariableMap();
        List<String> paths = yasaSamples.get(systemID);
        for (String path : paths) {
            Path yasaSamplePath = Paths.get(path);
            String fileNameWithoutExtension = IO.getFileNameWithoutExtension(yasaSamplePath);
            Path sampleFile =
                    genPath.resolve(modelName).resolve(modelVersionName).resolve("c_" + fileNameWithoutExtension);
            if (Files.exists(sampleFile)) {
                FeatJAR.log().info("Skipping %s (already exists)", modelName);
                taskCompleted();
                continue;
            }

            Result<BooleanAssignmentGroups> yasaSampleResult =
                    IO.load(yasaSamplePath, new BooleanAssignmentGroupsCompressedFormat());
            if (yasaSampleResult.isEmpty()) {
                FeatJAR.log().warning("Could not read file %s", yasaSamplePath);
                FeatJAR.log().problems(yasaSampleResult.getProblems(), Verbosity.WARNING);
                taskCompleted();
                continue;
            }
            List<? extends ABooleanAssignment> yasaSample =
                    yasaSampleResult.get().getGroups().get(0);
            ArrayList<BooleanSolution> combinedSample = new ArrayList<>(fieldSample.size() + yasaSample.size());
            VariableMap newVariableMap = fieldSampleVariableMap.clone();
            VariableMap yasaSampleVariableMap = yasaSampleResult.get().getVariableMap();
            newVariableMap.addAll(yasaSampleVariableMap);
            fieldSample.stream()
                     .map(a -> {
                        int[] adaptedLiterals =
		                        a.adapt(fieldSampleVariableMap, newVariableMap).orElseThrow();
                        int[] newLiterals = IntStream.range(0, newVariableMap.getVariableCount())
		                        .map(i -> -(i + 1))
		                        .toArray();
                        Arrays.stream(adaptedLiterals).forEach(l -> newLiterals[Math.abs(l) - 1] = l);
                        return new BooleanSolution(newLiterals);
                    })
                    .forEach(combinedSample::add);
            yasaSample.stream()
                    .map(a -> {
                        int[] adaptedLiterals =
                                a.adapt(yasaSampleVariableMap, newVariableMap).orElseThrow();
                        int[] newLiterals = IntStream.range(0, newVariableMap.getVariableCount())
                                .map(i -> -(i + 1))
                                .toArray();
                        Arrays.stream(adaptedLiterals).forEach(l -> newLiterals[Math.abs(l) - 1] = l);
                        return new BooleanSolution(newLiterals);
                    })
                    .forEach(combinedSample::add);
            for (BooleanSolution c : combinedSample) {
                if (Arrays.stream(c.get()).anyMatch(i -> i == 0)) {
                    FeatJAR.log().info(c);
                }
            }

            writeSample(
                    sampleFile,
                    Paths.get(modelVersionName)
                            .resolve("c_" + fileNameWithoutExtension)
                            .toString(),
                    combinedSample,
                    newVariableMap,
                    "c",
                    "",
                    1,
                    -1,
                    -1,
                    -1,
                    false,
                    false,
                    0);
            taskCompleted();
        }
    }
}
