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
 * The {@link VehicleStatus} dto describes the current status of the vehicle's doors, windows and
 * lights - an aggregated {@code overall} view plus per-part {@code detail}.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class VehicleStatus {

    public @Nullable OverallVehicleStatus overall;
    public @Nullable VehicleStatusDetail detail;
    public String carCapturedTimestamp = "";
}
