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
package de.featjar.evaluation.samplereducer.wrapper;

import de.featjar.evaluation.process.EvaluationAlgorithm;
import java.nio.file.Path;

public class ReduceAlgorithm extends EvaluationAlgorithm {

    private final int t;
    private final long seed;
    private final String algorithm;

    public ReduceAlgorithm(Path input, Path output, int t, long seed, String algorithm) {
        super(
                "evaluation-sample-reducer-0.1.0-SNAPSHOT-all",
                "de.featjar.evaluation.samplereducer.wrapper.ReduceCommand",
                input,
                output);
        this.t = t;
        this.seed = seed;
        this.algorithm = algorithm;
    }

    @Override
    public String getName() {
        return algorithm;
    }

    @Override
    public String getParameterSettings() {
        return "t" + t;
    }

    @Override
    protected void addCommandElements() throws Exception {
        super.addCommandElements();
        commandElements.add("--t");
        commandElements.add(String.valueOf(t));
        commandElements.add("--seed");
        commandElements.add(String.valueOf(seed));
        commandElements.add("--algorithm");
        commandElements.add(algorithm);
        commandElements.add("--print-stacktrace");
    }
}
