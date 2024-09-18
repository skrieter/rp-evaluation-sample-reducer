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
package de.featjar.evaluation.samplereducer.sampler;

import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanSolution;
import java.util.List;

public interface ISampleReducer {

    public static class Interaction extends BooleanAssignment {
        private static final long serialVersionUID = -6484298506539342496L;

        private int counter = 0;

        public Interaction(int... array) {
            super(array);
        }

        public void setCounter(int counter) {
            this.counter = counter;
        }

        public void increaseCounter() {
            counter++;
        }

        public void decreaseCounter() {
            counter--;
        }

        public int getCounter() {
            return counter;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    List<BooleanSolution> reduce(List<BooleanSolution> sample, int t);
}
