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

import de.featjar.analysis.sat4j.twise.CoverageStatistic;
import de.featjar.analysis.sat4j.twise.RelativeTWiseCoverageComputation;
import de.featjar.base.FeatJAR;
import de.featjar.base.cli.ListOption;
import de.featjar.base.cli.Option;
import de.featjar.base.computation.Computations;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.base.log.Log.Verbosity;
import de.featjar.evaluation.Evaluator;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.ABooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.assignment.BooleanSolutionList;
import de.featjar.formula.io.binary.BooleanAssignmentGroupsBinaryFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * In this phase, we run YASA on the read feature model
 *
 * @author Sebastian Krieter
 */
public class ComputeCoveragePhase extends Evaluator {

    private CSVFile coverageCSV;

    public static final ListOption<Integer> tOption = Option.newListOption("t", Option.IntegerParser);

    private String modelName;
    private int t;
    private LinkedHashMap<Integer, List<String>> samples;
    private LinkedHashMap<Integer, LinkedHashMap<Integer, String>> yasaSamples;
    private LinkedHashMap<Integer, String> fieldSamples;
    private LinkedHashMap<String, Integer> sampleIDs;

    private int fileCount, fileId;

    @Override
    protected void runEvaluation() throws Exception {
        coverageCSV = new CSVFile(dataPath.resolve("coverage.csv"), true);
        coverageCSV.setHeaderFields("SampleID", "VariableCount", "CoverageType", "T", "Coverage");

        samples = new LinkedHashMap<>();
        fieldSamples = new LinkedHashMap<>();
        yasaSamples = new LinkedHashMap<>();
        sampleIDs = new LinkedHashMap<>();
        try {
            CSVFile.readAllLines(dataPath.resolve("samples.csv")).skip(1).forEach(l -> {
                int sampleID = Integer.parseInt(l.get(0));
                int systemID = Integer.parseInt(l.get(1));
                String samplePath = genPath.resolve(systemNames.get(systemID))
                        .resolve(l.get(3))
                        .toString();
                if (samplePath == null || samplePath.isBlank()) {
                    FeatJAR.log().warning("No path for sample %d", sampleID);
                    return;
                }
                if (l.get(4).equals("f")) {
                    fieldSamples.put(systemID, samplePath);
                } else if (l.get(4).equals("t")) {
                    LinkedHashMap<Integer, String> map = yasaSamples.get(systemID);
                    if (map == null) {
                        map = new LinkedHashMap<>();
                        yasaSamples.put(systemID, map);
                    }
                    map.put(Integer.parseInt(l.get(7)), samplePath);
                }
                List<String> list = samples.get(systemID);
                if (list == null) {
                    list = new ArrayList<>();
                    samples.put(systemID, list);
                }
                list.add(samplePath);
                sampleIDs.put(samplePath, sampleID);
            });
        } catch (Exception e) {
            FeatJAR.log().error("Could not read samples.csv.");
            FeatJAR.log().error(e);
            return;
        }

        optionCombiner.init(systemsOption, tOption);
        optionCombiner.loopOverOptions(this::optionLoop);
    }

    public int optionLoop(int lastChanged) {
        switch (lastChanged) {
            case 0:
                modelName = optionCombiner.getValue(0);
            case 1:
                t = optionCombiner.getValue(1);
        }
        try {
            Path modelPath = genPath.resolve(modelName);
            if (!Files.exists(modelPath)) {
                FeatJAR.log().info("Skipping %s (does not exist)", modelName);
                return 1;
            }
            List<String> modeFiles = Files.walk(modelPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".dimacs"))
                    .map(Path::toString)
                    .collect(Collectors.toList());
            Collections.sort(modeFiles);

            int systemID = getSystemId(modelName);
            List<String> sampleFiles = samples.get(systemID);
            fileCount = 2 * sampleFiles.size();
            fileId = 0;
            FeatJAR.log().progress("  0.0%");

            twiseCoverage(modelPath, systemID, sampleFiles);
            fieldCoverage(systemID, sampleFiles);
        } catch (Exception e) {
            FeatJAR.log().error(e);
            return 1;
        }
        return -1;
    }

    private void fieldCoverage(int systemID, List<String> sampleFiles) {
        Path fieldSamplePath = Path.of(fieldSamples.get(systemID));
        if (fieldSamplePath == null || !Files.exists(fieldSamplePath)) {
            FeatJAR.log().warning("No field sample, skipping field coverage for %s", modelName);
            fileId += sampleFiles.size();
            FeatJAR.log().progress("%5.1f%%", (100.0 * fileId) / fileCount);
            return;
        }
        Result<BooleanAssignmentGroups> fieldSampleResult =
                IO.load(fieldSamplePath, new BooleanAssignmentGroupsBinaryFormat());
        if (fieldSampleResult.isEmpty()) {
            FeatJAR.log().warning("Could not read file %s", fieldSamplePath);
            FeatJAR.log().problems(fieldSampleResult.getProblems(), Verbosity.WARNING);
            fileId += sampleFiles.size();
            FeatJAR.log().progress("%5.1f%%", (100.0 * fileId) / fileCount);
            return;
        }
        VariableMap fieldVariableMap = fieldSampleResult.get().getVariableMap();
        BooleanSolutionList fieldSample = new BooleanSolutionList(fieldSampleResult.get().getGroups().get(0).stream()
                .map(ABooleanAssignment::toSolution)
                .collect(Collectors.toList()));
        for (String pathName : sampleFiles) {
            try {
                computeCoverage("f", fieldVariableMap, fieldSample, Path.of(pathName));
            } catch (Exception e) {
                FeatJAR.log().error("Could not analyze field coverage for %s", pathName);
                FeatJAR.log().error(e);
            }
            FeatJAR.log().progress("%5.1f%%", (100.0 * ++fileId) / fileCount);
        }
    }

    private void twiseCoverage(Path modelPath, int systemID, List<String> sampleFiles) {
        LinkedHashMap<Integer, String> yasaSamplesPerT = yasaSamples.get(systemID);
        if (yasaSamplesPerT == null) {
            FeatJAR.log().info("No YASA sample, skipping t-wise coverage for %s", modelName);
            fileId += sampleFiles.size();
            FeatJAR.log().progress("%5.1f%%", (100.0 * fileId) / fileCount);
            return;
        }

        String yasaSamplePathName = yasaSamplesPerT.get(t);
        if (yasaSamplePathName == null) {
            FeatJAR.log().info("Skipping YASA sample for t = %d (does not exist)", t);
            fileId += sampleFiles.size();
            FeatJAR.log().progress("%5.1f%%", (100.0 * fileId) / fileCount);
            return;
        }

        Path yasaSamplePath = Path.of(yasaSamplePathName);
        if (yasaSamplePath == null || !Files.exists(yasaSamplePath)) {
            FeatJAR.log().info("Skipping YASA sample for t = %d (does not exist)", t);
            fileId += sampleFiles.size();
            FeatJAR.log().progress("%5.1f%%", (100.0 * fileId) / fileCount);
            return;
        }

        Result<BooleanAssignmentGroups> yasaSampleResult =
                IO.load(yasaSamplePath, new BooleanAssignmentGroupsBinaryFormat());
        if (yasaSampleResult.isEmpty()) {
            FeatJAR.log().warning("Could not read file %s", yasaSamplePath);
            FeatJAR.log().problems(yasaSampleResult.getProblems(), Verbosity.WARNING);
            fileId += sampleFiles.size();
            FeatJAR.log().progress("%5.1f%%", (100.0 * fileId) / fileCount);
            return;
        }

        VariableMap fieldVariableMap = yasaSampleResult.get().getVariableMap();
        BooleanSolutionList fieldSample = new BooleanSolutionList(yasaSampleResult.get().getGroups().get(0).stream()
                .map(ABooleanAssignment::toSolution)
                .collect(Collectors.toList()));
        for (String pathName : sampleFiles) {
            try {
                computeCoverage("t", fieldVariableMap, fieldSample, Path.of(pathName));
            } catch (Exception e) {
                FeatJAR.log().error("Could not analyze t-wise coverage for %s", pathName);
                FeatJAR.log().error(e);
            }
            FeatJAR.log().progress("%5.1f%%", (100.0 * ++fileId) / fileCount);
        }
    }

    private void computeCoverage(
            String type, VariableMap referenceVariableMap, BooleanSolutionList referenceSample, Path samplePath) {
        if (!Files.exists(samplePath)) {
            FeatJAR.log().warning("No sample file %s", samplePath);
            return;
        }
        Result<BooleanAssignmentGroups> otherSampleResult =
                IO.load(samplePath, new BooleanAssignmentGroupsBinaryFormat());
        if (otherSampleResult.isEmpty()) {
            FeatJAR.log().warning("Could not read file %s", samplePath);
            FeatJAR.log().problems(otherSampleResult.getProblems(), Verbosity.WARNING);
            return;
        }
        VariableMap otherVariableMap = otherSampleResult.get().getVariableMap();

        VariableMap newVariableMap = referenceVariableMap.clone();
        BooleanSolutionList otherSample = new BooleanSolutionList(otherSampleResult.get().getGroups().get(0).stream()
                .map(s -> {
                    int[] adaptedLiterals = ABooleanAssignment.adaptAddVariables(
                                    s.get(), otherVariableMap, newVariableMap)
                            .orElseThrow();
                    int[] newLiterals = IntStream.range(0, newVariableMap.getVariableCount())
                            .map(i -> -(i + 1))
                            .toArray();
                    Arrays.stream(adaptedLiterals).forEach(l -> newLiterals[Math.abs(l) - 1] = l);
                    return new BooleanSolution(newLiterals);
                })
                .collect(Collectors.toList()));

        CoverageStatistic fieldCoverage = Computations.of(referenceSample)
                .map(RelativeTWiseCoverageComputation::new)
                .set(RelativeTWiseCoverageComputation.T, t)
                .set(RelativeTWiseCoverageComputation.SAMPLE, otherSample)
                .compute();

        CSVFile.writeCSV(coverageCSV, w -> {
            w.add(sampleIDs.get(samplePath.toString()));
            w.add(otherVariableMap.getVariableCount());
            w.add(type);
            w.add(t);
            w.add(fieldCoverage.coverage());
        });
    }
}
