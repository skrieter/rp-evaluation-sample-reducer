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
import de.featjar.evaluation.Evaluator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author Sebastian Krieter
 */
public class DeleteUnecessaryTPFilesPhase extends Evaluator {

    @Override
    protected void runEvaluation() throws Exception {
        optionCombiner.init(systemsOption);
        optionCombiner.loopOverOptions(this::optionLoop);
    }

    public int optionLoop(int lastChanged) {
        String modelName = optionCombiner.getValue(0);
        Path modelDir = modelPath.resolve(modelName);
        if (!Files.exists(modelDir)) {
            FeatJAR.log().warning("Skipping %s (does not exist)", modelName);
            return 0;
        }
        try {
            Files.walk(modelDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> !"clean.dimacs".equals(p.getFileName().toString()))
                    .filter(p -> !p.getParent().endsWith(Path.of("random").resolve("0")))
                    .forEach(this::deleteFile);
            Files.walk(modelDir).filter(Files::isDirectory).forEach(this::deleteDirectory);
        } catch (IOException e) {
            FeatJAR.log().error(e);
            return 0;
        }
        return -1;
    }

    private void deleteDirectory(Path path) {
        try {
            if (Files.list(path).count() == 0) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
        }
    }
}
