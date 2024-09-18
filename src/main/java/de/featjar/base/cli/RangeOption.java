/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-base.
 *
 * base is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * base is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with base. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-base> for further information.
 */
package de.featjar.base.cli;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A range option, representing a list of integer values from 1 to n.
 *
 * @author Sebastian Krieter
 */
public class RangeOption extends AListOption<Integer> {

    /**
     * Creates a range option.
     *
     * @param name the name
     */
    protected RangeOption(String name) {
        super(name, s -> IntStream.rangeClosed(1, Integer.parseInt(s)).boxed().collect(Collectors.toList()), 1);
    }
}
