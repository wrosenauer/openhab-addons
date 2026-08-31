/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.myskoda.internal.api.dto;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link EngineRange} dto describes a single engine (electric or combustion) of a
 * vehicle's {@link FuelStatus}, e.g. the primary or secondary engine of a plug-in hybrid.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class EngineRange {

    public String engineType = "";
    public @Nullable Double currentSoCInPercent;
    public @Nullable Double currentFuelLevelInPercent;
    public @Nullable Double remainingRangeInKm;
}
