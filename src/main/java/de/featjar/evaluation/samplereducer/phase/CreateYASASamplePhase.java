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

import de.featjar.analysis.sat4j.computation.YASA;
import de.featjar.base.FeatJAR;
import de.featjar.base.computation.Computations;
import de.featjar.base.data.Pair;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.log.Log.Verbosity;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.BooleanSolutionList;
import de.featjar.formula.assignment.ComputeBooleanClauseList;
import de.featjar.formula.io.dimacs.FormulaDimacsFormat;
import de.featjar.formula.structure.IFormula;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * In this phase, we run YASA on the read feature model
 *
 * @author Sebastian Krieter
 */
public class CreateYASASamplePhase extends ACreateSamplePhase {

    @Override
    protected void runOptionLoop() {
        optionCombiner.init(tOption, systemsOption);
        optionCombiner.loopOverOptions(this::optionLoop);
    }

    @Override
    protected void setOptions(int lastChanged) {
        switch (lastChanged) {
            case 0:
                t = optionCombiner.getValue(0);
            case 1:
                modelName = optionCombiner.getValue(1);
        }
    }

    protected void processFile(Path path) {
        Path yasaFile = genPath.resolve(modelName).resolve(modelVersionName).resolve("y_t" + t);
        if (Files.exists(yasaFile)) {
            FeatJAR.log().info("Skipping %s (already exists)", modelName);
            taskCompleted();
            return;
        }

        Result<IFormula> formula = IO.load(path, new FormulaDimacsFormat());
        if (formula.isEmpty()) {
            FeatJAR.log().warning("Could not read file %s", path);
            FeatJAR.log().problems(formula.getProblems(), Verbosity.WARNING);
            taskCompleted();
            return;
        }

        long start = System.nanoTime();
        Pair<BooleanClauseList, VariableMap> rep =
                formula.toComputation().map(ComputeBooleanClauseList::new).compute();
        long end = System.nanoTime();
        BooleanSolutionList sample =
                Computations.of(rep.getKey()).map(YASA::new).set(YASA.T, t).compute();
        writeSample(
                yasaFile,
                Paths.get(modelVersionName).resolve("y_t" + t).toString(),
                sample.getAll(),
                rep.getValue(),
                "t",
                "yasa",
                1,
                t,
                end - start,
                -1,
                false,
                false,
                0);
        taskCompleted();
    }
}
