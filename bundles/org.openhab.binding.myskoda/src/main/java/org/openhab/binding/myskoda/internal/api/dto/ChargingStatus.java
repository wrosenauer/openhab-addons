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
 * The {@link ChargingStatus} dto holds the live charging state of the vehicle.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class ChargingStatus {

    public @Nullable Double chargingRateInKilometersPerHour;
    public @Nullable Double chargePowerInKw;
    public @Nullable Integer remainingTimeToFullyChargedInMinutes;
    public String fullyChargedAt = "";
    public String state = "";
    public String chargeType = "";
    public @Nullable BatteryStatus battery;
}
