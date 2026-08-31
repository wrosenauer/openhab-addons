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
 * The {@link AuxiliaryHeating} dto reports the state of the vehicle's auxiliary (fuel-operated or
 * electric) heater.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class AuxiliaryHeating {

    public String state = "";
    public String startMode = "";
    public @Nullable Integer durationInSeconds;
    public @Nullable TargetTemperature targetTemperature;
    public String estimatedReachOfTargetTemperatureAt = "";
    public String carCapturedTimestamp = "";
}
