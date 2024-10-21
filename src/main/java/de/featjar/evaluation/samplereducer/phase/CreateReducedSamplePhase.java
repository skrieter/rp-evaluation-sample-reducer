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
import de.featjar.base.cli.ListOption;
import de.featjar.base.cli.Option;
import de.featjar.base.data.Pair;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.base.log.Log.Verbosity;
import de.featjar.evaluation.process.IProcessRunner;
import de.featjar.evaluation.process.ProcessResult;
import de.featjar.evaluation.process.ProcessRunner;
import de.featjar.evaluation.samplereducer.wrapper.ReduceAlgorithm;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.io.binary.BooleanAssignmentGroupsCompressedFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * In this phase, we run YASA on the read feature model
 *
 * @author Sebastian Krieter
 */
public class CreateReducedSamplePhase extends ACreateSamplePhase {

    public static final ListOption<String> algorithms = Option.newListOption("algorithms", Option.StringParser);

    private LinkedHashMap<Integer, List<Pair<String, Integer>>> samples;

    private boolean replacePreviousResults;
    private IProcessRunner processRunner;
    private String algorithmName;

    @Override
    protected void initEval() {
        samples = new LinkedHashMap<>();
        try {
            CSVFile.readAllLines(dataPath.resolve("samples.csv")).skip(1).forEach(l -> {
                int systemID = Integer.parseInt(l.get(1));
                String type = l.get(4);
                if (!type.equals("r")) {
                    List<Pair<String, Integer>> list = samples.get(systemID);
                    if (list == null) {
                        list = new ArrayList<>();
                        samples.put(systemID, list);
                    }
                    int sampleID = Integer.parseInt(l.get(0));
                    String path = genPath.resolve(systemNames.get(systemID))
                            .resolve(l.get(3))
                            .toString();
                    list.add(new Pair<>(path, sampleID));
                }
            });
        } catch (Exception e) {
            FeatJAR.log().error("Could not read samples.csv.");
            FeatJAR.log().error(e);
            throw new RuntimeException(e);
        }
        processRunner = new ProcessRunner();
        processRunner.setTimeout(optionParser.get(timeout));
        replacePreviousResults = optionParser.get(overwrite);
    }

    @Override
    protected void runOptionLoop() {
        optionCombiner.init(tOption, systemsOption, algorithms, algorithmIterationsOption);
        optionCombiner.loopOverOptions(this::optionLoop);
    }

    protected void setOptions(int lastChanged) {
        switch (lastChanged) {
            case 0:
                t = optionCombiner.getValue(0);
            case 1:
                modelName = optionCombiner.getValue(1);
            case 2:
                algorithmName = optionCombiner.getValue(2);
            case 3:
                algorithmIteration = optionCombiner.getValue(3);
        }
    }

    @Override
    protected int getTaskCount() {
        List<Pair<String, Integer>> sampleList = samples.get(getSystemId(modelName));
        return sampleList != null ? sampleList.size() : 0;
    }

    @Override
    protected void processFile(Path modelDir) {
        for (Pair<String, Integer> sample : samples.get(getSystemId(modelName))) {
            Path path = Paths.get(sample.getKey());
            computeReducedSample(path, processRunner, algorithmName, sample.getValue());
            taskCompleted();
        }
    }

    private void computeReducedSample(Path path, IProcessRunner runner, String algorithmName, int originalSampmleID) {
        String fileName = IO.getFileNameWithoutExtension(path);
        String reducedFileName = fileName + "_r_" + algorithmName + "_i" + algorithmIteration + "_s" + seed + "_t" + t;
        Path reducedFile = genPath.resolve(modelName).resolve(modelVersionName).resolve(reducedFileName);
        if (!replacePreviousResults && Files.exists(reducedFile)) {
            FeatJAR.log().info("Skipping %s (already exists)", modelName);
            return;
        }
        ProcessResult<Void> run =
                runner.run(new ReduceAlgorithm(path, reducedFile, t, seed + algorithmIteration, algorithmName));
        if (!run.isNoError()) {
            FeatJAR.log().warning("Error during reduction occured", reducedFile);
            writeSampleCSVEntry(
                    Paths.get(modelVersionName).resolve(reducedFileName).toString(),
                    0,
                    "r",
                    algorithmName,
                    algorithmIteration,
                    t,
                    -1,
                    originalSampmleID,
                    true,
                    false,
                    seed);
            return;
        }
        if (!run.isTerminatedInTime()) {
            FeatJAR.log().warning("Timeout during reduction occured", reducedFile);
            writeSampleCSVEntry(
                    Paths.get(modelVersionName).resolve(reducedFileName).toString(),
                    0,
                    "r",
                    algorithmName,
                    algorithmIteration,
                    t,
                    -1,
                    originalSampmleID,
                    false,
                    true,
                    seed);
            return;
        }
        Result<BooleanAssignmentGroups> reducedSample = IO.load(reducedFile, new BooleanAssignmentGroupsCompressedFormat());
        if (reducedSample.isEmpty()) {
            FeatJAR.log().warning("Could not read file %s", reducedFile);
            FeatJAR.log().problems(reducedSample.getProblems(), Verbosity.WARNING);
            return;
        }
        Path timePath = reducedFile.resolveSibling("time");
        double time;
        try {
            time = Double.parseDouble(Files.readAllLines(timePath).get(0));
        } catch (Exception e) {
            FeatJAR.log().error(e);
            return;
        }
        writeSampleCSVEntry(
                Paths.get(modelVersionName).resolve(reducedFileName).toString(),
                reducedSample.get().getGroups().get(0).size(),
                "r",
                algorithmName,
                algorithmIteration,
                t,
                time,
                originalSampmleID,
                false,
                false,
                seed);
    }
}
