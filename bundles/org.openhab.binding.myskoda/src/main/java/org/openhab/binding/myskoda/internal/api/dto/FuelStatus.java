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
 * The {@link FuelStatus} dto reports fuel status and driving range, returned only for vehicles
 * with a combustion engine (including hybrids). Battery-electric vehicles report range and state
 * of charge via {@link Charging} instead.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class FuelStatus {

    public String carType = "";
    public @Nullable Double adBlueRange;
    public @Nullable Double totalRangeInKm;
    public @Nullable EngineRange primaryEngineRange;
    public @Nullable EngineRange secondaryEngineRange;
    public String carCapturedTimestamp = "";
}
