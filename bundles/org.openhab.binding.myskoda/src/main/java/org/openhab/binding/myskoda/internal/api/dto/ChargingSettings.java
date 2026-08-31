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
 * The {@link ChargingSettings} dto reports the vehicle's configured charging preferences. These
 * are informational only - the public API has no endpoint to change them, so all of them surface
 * as read-only channels.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class ChargingSettings {

    public @Nullable Integer targetStateOfChargeInPercent;
    public @Nullable Integer batteryCareModeTargetValueInPercent;
    public String preferredChargeMode = "";
    public String chargingCareMode = "";
    public String autoUnlockPlugWhenCharged = "";
    public String maxChargeCurrentAc = "";
    public @Nullable Integer maxChargeCurrentAcAmpere;
}
