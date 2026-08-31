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
 * The {@link StartAuxiliaryHeatingConfiguration} dto is the request body of
 * {@code POST /auxiliary-heating/start}. The vehicle's security PIN ({@code spin}) is required.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class StartAuxiliaryHeatingConfiguration {

    public @Nullable TargetTemperature targetTemperature;
    public String spin;
    public int durationInSeconds;
    public String startMode;

    public StartAuxiliaryHeatingConfiguration(String spin, int durationInSeconds, String startMode) {
        this.spin = spin;
        this.durationInSeconds = durationInSeconds;
        this.startMode = startMode;
    }
}
