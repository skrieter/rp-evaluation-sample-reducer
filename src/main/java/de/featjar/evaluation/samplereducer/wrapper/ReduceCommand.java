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

import de.featjar.analysis.AAnalysisCommand;
import de.featjar.base.FeatJAR;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.OptionList;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.format.IFormat;
import de.featjar.base.log.Log.Verbosity;
import de.featjar.evaluation.samplereducer.sampler.BitSetCounterSampleReducer;
import de.featjar.evaluation.samplereducer.sampler.BitSetScoring1SampleReducer;
import de.featjar.evaluation.samplereducer.sampler.BitSetScoring2SampleReducer;
import de.featjar.evaluation.samplereducer.sampler.ISampleReducer;
import de.featjar.evaluation.samplereducer.sampler.NewSampleReducer;
import de.featjar.evaluation.samplereducer.sampler.RandomSampleReducer;
import de.featjar.evaluation.samplereducer.sampler.SampleReducer;
import de.featjar.formula.assignment.ABooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.io.binary.BooleanAssignmentGroupsBinaryFormat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReduceCommand extends AAnalysisCommand<BooleanAssignmentGroups> {

    public static final Option<Integer> T_OPTION = Option.newOption("t", Option.IntegerParser) //
            .setDescription("T used for reduction");

    public static final Option<String> ALGORITHM_OPTION = Option.newOption("algorithm", Option.StringParser) //
            .setDescription("Class name of algorithm used for reduction");

    /**
     * Option for setting the seed for the pseudo random generator.
     */
    public static final Option<Long> RANDOM_SEED_OPTION = Option.newOption("seed", Option.LongParser) //
            .setDescription("Seed for the pseudo random generator") //
            .setDefaultValue(1L);

    private Path inputPath;
    private ISampleReducer reducer;

    private int t;

    @Override
    protected IComputation<BooleanAssignmentGroups> newComputation(OptionList optionParser) {
        String algorithmName = optionParser.get(ALGORITHM_OPTION);
        switch (algorithmName) {
            case "BitSetCounterSampleReducer":
                reducer = new BitSetCounterSampleReducer();
                break;
            case "BitSetScoring1SampleReducer":
                reducer = new BitSetScoring1SampleReducer();
                break;
            case "BitSetScoring2SampleReducer":
                reducer = new BitSetScoring2SampleReducer();
                break;
            case "NewSampleReducer":
                reducer = new NewSampleReducer();
                break;
            case "SampleReducer":
                reducer = new SampleReducer();
                break;
            case "RandomSampleReducer":
                reducer = new RandomSampleReducer(optionParser.get(RANDOM_SEED_OPTION));
                break;
            default:
                throw new IllegalArgumentException(algorithmName);
        }
        inputPath = optionParser.get(INPUT_OPTION);
        t = optionParser.get(T_OPTION);

        return Computations.of(ReduceCommand.class, "", this::computeReducedSample);
    }

    private Result<BooleanAssignmentGroups> computeReducedSample() {
        Result<BooleanAssignmentGroups> sample = IO.load(inputPath, new BooleanAssignmentGroupsBinaryFormat());
        if (sample.isEmpty()) {
            FeatJAR.log().warning("Could not read file %s", inputPath);
            FeatJAR.log().problems(sample.getProblems(), Verbosity.WARNING);
            return Result.empty(sample.getProblems());
        } else {
            List<BooleanSolution> oldSample = sample.get().getGroups().get(0).stream()
                    .map(ABooleanAssignment::toSolution)
                    .collect(Collectors.toList());

            List<BooleanSolution> reducedSample = reducer.reduce(new ArrayList<>(oldSample), t);
            return Result.of(new BooleanAssignmentGroups(sample.get().getVariableMap(), List.of(reducedSample)));
        }
    }

    @Override
    protected IFormat<?> getOuputFormat() {
        return new BooleanAssignmentGroupsBinaryFormat();
    }
}
