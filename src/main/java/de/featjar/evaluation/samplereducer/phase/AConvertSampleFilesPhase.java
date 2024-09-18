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
import de.featjar.base.log.Log.Verbosity;
import de.featjar.evaluation.Evaluator;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.io.binary.BooleanAssignmentGroupsBinaryFormat;
import de.featjar.formula.io.dimacs.FormulaDimacsFormat;
import de.featjar.formula.structure.IFormula;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * In this phase, we read and prepare the configurations for the evaluation and run the sampleReducer for the field configurations
 *
 * @author Rahel Sundermann
 * @author Sebastian Krieter
 */
public abstract class AConvertSampleFilesPhase extends Evaluator {

    public static final Comparator<? super Path> fileComparator =
            (p1, p2) -> p1.toString().compareTo(p2.toString());

    protected CSVFile modelCSV, systemCSV, samplesCSV;
    protected String modelName, modelVersionName;
    protected int modelId, sampleId;

    @Override
    protected void runEvaluation() throws Exception {
        final Path modelCSVFile = dataPath.resolve("models.csv");
        final Path systemCSVFile = dataPath.resolve("systems.csv");
        final Path sampleCSVFile = dataPath.resolve("samples.csv");

        modelId = readMaxCSVId(modelCSVFile);
        int systemId = readMaxCSVId(systemCSVFile);
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

        modelCSV = new CSVFile(modelCSVFile, true);
        if (modelId == -1) {
            modelCSV.setHeaderFields("ID", "SystemID", "Version", "VariableCount", "ClauseCount");
        }

        systemCSV = new CSVFile(systemCSVFile, true);
        if (systemId == -1) {
            systemCSV.setHeaderFields("ID", "Name");
        }

        optionCombiner.init(systemsOption);
        optionCombiner.loopOverOptions(this::optionLoop);
    }

    public int optionLoop(int lastChanged) {
        modelName = optionCombiner.getValue(0);

        int systemID = getSystemId(modelName);
        if (systemID == -1) {
            FeatJAR.log().warning("System %s does not exit", modelName);
        }

        CSVFile.writeCSV(systemCSV, w -> {
            w.add(systemID);
            w.add(modelName);
        });
        try {
            List<Path> modelFiles = collectModelFiles();
            int fileCount = modelFiles.size() + 1;
            int fileId = 0;
            FeatJAR.log().progress("  0.0%");
            for (Path path : modelFiles) {
                processFiles(path);
                FeatJAR.log().progress("%5.1f%%", (100.0 * ++fileId) / fileCount);
            }
            convertSample();
            FeatJAR.log().progress("%5.1f%%", (100.0 * ++fileId) / fileCount);
        } catch (IOException e) {
            FeatJAR.log().error(e);
            return 0;
        }
        return -1;
    }

    private void processFiles(Path path) {
        modelVersionName = getModelVersion(path);
        Path convertedModelFile =
                genPath.resolve(modelName).resolve(modelVersionName).resolve("model.dimacs");
        if (Files.exists(convertedModelFile)) {
            FeatJAR.log().info("Skipping %s (already exists)", modelName);
            return;
        }
        try {
            if (Files.size(path) == 0) {
                return;
            }
        } catch (IOException e) {
            FeatJAR.log().error(e);
        }
        Result<IFormula> formula = loadFormula(path);
        if (formula.isEmpty()) {
            FeatJAR.log().warning("Could not read file %s", path);
            FeatJAR.log().problems(formula.getProblems(), Verbosity.WARNING);
        } else {
            VariableMap variables = VariableMap.of(formula.get());
            try {
                IO.save(formula.get(), convertedModelFile, new FormulaDimacsFormat());
            } catch (IOException e) {
                FeatJAR.log().warning(e);
                return;
            }
            modelId++;
            CSVFile.writeCSV(modelCSV, w -> {
                w.add(modelId);
                w.add(getSystemId(modelName));
                w.add(modelVersionName);
                w.add(variables.getVariableCount());
                w.add(formula.get().getChildren().get(0).getChildrenCount());
            });
        }
    }

    private void convertSample() {
        Path convertedSampleFile = genPath.resolve(modelName).resolve("f");
        if (Files.exists(convertedSampleFile)) {
            FeatJAR.log().info("Skipping %s (already exists)", convertedSampleFile);
            return;
        }

        Pair<List<BooleanSolution>, VariableMap> sample;
        try {
            sample = loadFieldSample();
            IO.save(
                    new BooleanAssignmentGroups(sample.getValue(), List.of(sample.getKey())),
                    convertedSampleFile,
                    new BooleanAssignmentGroupsBinaryFormat());
        } catch (IOException e) {
            FeatJAR.log().warning(e);
            return;
        }

        sampleId++;
        CSVFile.writeCSV(samplesCSV, w -> {
            w.add(sampleId);
            w.add(getSystemId(modelName));
            w.add(-1);
            w.add("f");
            w.add("f");
            w.add("");
            w.add(1);
            w.add(0);
            w.add(sample.getKey().size());
            w.add(-1);
            w.add(-1);
            w.add(false);
            w.add(false);
            w.add(0);
        });
    }

    protected abstract Pair<List<BooleanSolution>, VariableMap> loadFieldSample() throws IOException;

    protected abstract List<Path> collectModelFiles() throws IOException;

    protected abstract Result<IFormula> loadFormula(Path path);

    protected abstract String getModelVersion(Path path);
}
