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

/**
 * The {@link ChargingSettings} dto reports the vehicle's configured charging preferences. Only the
 * target state of charge ({@code PUT /charging/limit}) and the preferred charge mode
 * ({@code PUT /charging/mode}) can be changed through the public API; the others are read-only.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class ChargingSettings {

    public @Nullable Integer targetStateOfChargeInPercent;
    public @Nullable Integer batteryCareModeTargetValueInPercent;
    public String preferredChargeMode = "";
    public @Nullable List<String> availableChargeModes;
    public String chargingCareMode = "";
    public String autoUnlockPlugWhenCharged = "";
    public String maxChargeCurrentAc = "";
    public @Nullable Integer maxChargeCurrentAcAmpere;
}
