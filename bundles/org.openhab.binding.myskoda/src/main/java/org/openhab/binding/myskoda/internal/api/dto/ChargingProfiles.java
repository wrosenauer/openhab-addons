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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonObject;

/**
 * The {@link ChargingProfiles} dto lists the charging profiles (saved charging locations) of a
 * vehicle. The profiles are kept as raw JSON: {@code PUT /charging-profiles/{id}} replaces a
 * profile as a whole, so a changed profile must be sent back with all its fields - including the
 * ones this binding does not know about. {@link ChargingProfile} gives typed read access.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class ChargingProfiles {

    public List<JsonObject> profiles = List.of();
    public @Nullable CurrentVehiclePositionProfile currentVehiclePositionProfile;
    public String carCapturedTimestamp = "";
}
