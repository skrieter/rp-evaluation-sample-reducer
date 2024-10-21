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
import de.featjar.base.io.IO;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.Evaluator;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.io.binary.BooleanAssignmentGroupsCompressedFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In this phase, we run YASA on the read feature model
 *
 * @author Sebastian Krieter
 */
public abstract class ACreateSamplePhase extends Evaluator {

    public static final ListOption<Integer> tOption = Option.newListOption("t", Option.IntegerParser);

    protected CSVFile samplesCSV;

    protected String modelName, modelVersionName;
    protected int sampleId;
    protected LinkedHashMap<String, Integer> modelMap;

    protected int fileCount, taskID, algorithmIteration, t;
    protected long seed;

    @Override
    protected void runEvaluation() throws Exception {
        Path sampleCSVFile = dataPath.resolve("samples.csv");

        sampleId = readMaxCSVId(sampleCSVFile);
        samplesCSV = new CSVFile(sampleCSVFile, true);
        if (sampleId == -1) {
            samplesCSV.setHeaderFields(
                    "ID",
                    "SystemID",
                    "ModelID",
                    "Path",
                    "Type",
                    "Algorithm",
                    "AlgorithmIt",
                    "T",
                    "Size",
                    "Time",
                    "OriginalID",
                    "Error",
                    "Timeout",
                    "Seed");
        }

        modelMap = new LinkedHashMap<>();
        try {
            CSVFile.readAllLines(dataPath.resolve("models.csv"))
                    .skip(1)
                    .forEach(l -> modelMap.put(
                            systemNames.get(Integer.parseInt(l.get(1))) + ";" + l.get(2), Integer.parseInt(l.get(0))));
        } catch (Exception e) {
            FeatJAR.log().error("Could not read models.csv. Run prepare phase first.");
            return;
        }

        algorithmIteration = 1;
        t = 0;
        seed = optionParser.getResult(randomSeed).orElse(1L);

        initEval();

        runOptionLoop();
    }

    protected abstract void runOptionLoop();

    protected void initEval() {}

    protected abstract void setOptions(int lastChanged);

    public int optionLoop(int lastChanged) {
        setOptions(lastChanged);
        try {
            Path modelPath = genPath.resolve(modelName);
            if (!Files.exists(modelPath)) {
                FeatJAR.log().info("Skipping %s (does not exist)", modelName);
                return 0;
            }
            List<String> modeFiles = Files.walk(modelPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".dimacs"))
                    .sorted(AConvertSampleFilesPhase.fileComparator)
                    .map(Path::toString)
                    .collect(Collectors.toList());
            Collections.sort(modeFiles);
            String lastModel = modeFiles.get(modeFiles.size() - 1);
            Path path = Path.of(lastModel);
            modelVersionName = path.getName(path.getNameCount() - 2).toString();
            fileCount = getTaskCount();
            taskID = 0;
            FeatJAR.log().progress("  0.0%");
            processFile(path);
        } catch (Exception e) {
            FeatJAR.log().error(e);
            return 0;
        }
        return -1;
    }

    protected void taskCompleted() {
        FeatJAR.log().progress("%5.1f%%", (100.0 * ++taskID) / fileCount);
    }

    protected int getTaskCount() {
        return 1;
    }

    protected abstract void processFile(Path path);

    protected void writeSample(
            Path path,
            String relativePath,
            List<BooleanSolution> sample,
            VariableMap variables,
            String type,
            String algorithm,
            int algorithmIteration,
            int t,
            long time,
            int originalID,
            boolean errorOccured,
            boolean timeoutOccured,
            long seed) {
        try {
            IO.save(
                    new BooleanAssignmentGroups(variables, List.of(sample)),
                    path,
                    new BooleanAssignmentGroupsCompressedFormat());
            writeSampleCSVEntry(
                    relativePath,
                    sample.size(),
                    type,
                    algorithm,
                    algorithmIteration,
                    t,
                    time,
                    originalID,
                    errorOccured,
                    timeoutOccured,
                    seed);
        } catch (IOException e) {
            FeatJAR.log().error(e);
        }
    }

    protected void writeSampleCSVEntry(
            String path,
            int sampleSize,
            String type,
            String algorithm,
            int algorithmIteration,
            int t,
            double time,
            int originalID,
            boolean errorOccured,
            boolean timeoutOccured,
            long seed) {
        sampleId++;
        CSVFile.writeCSV(samplesCSV, w -> {
            w.add(sampleId);
            w.add(getSystemId(modelName));
            w.add(modelMap.get(modelName + ";" + modelVersionName));
            w.add(path);
            w.add(type);
            w.add(algorithm);
            w.add(algorithmIteration);
            w.add(t);
            w.add(sampleSize);
            w.add(time);
            w.add(originalID);
            w.add(errorOccured);
            w.add(timeoutOccured);
            w.add(seed);
        });
    }
}
