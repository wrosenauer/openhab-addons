/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
 * The {@link Charging} dto reports charging and battery status, returned only for vehicles that
 * support charging (battery-electric and plug-in hybrid).
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class Charging {

    public boolean isVehicleInSavedLocation;
    public @Nullable ChargingStatus status;
    public @Nullable ChargingSettings settings;
    public String carCapturedTimestamp = "";
}
