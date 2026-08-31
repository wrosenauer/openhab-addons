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

/**
 * The {@link TargetTemperature} dto is a target cabin temperature, used both when reporting
 * climate state and as a parameter of the air-conditioning/auxiliary-heating start commands.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class TargetTemperature {

    public double value;
    public String unit = "CELSIUS";

    public TargetTemperature() {
    }

    public TargetTemperature(double value, String unit) {
        this.value = value;
        this.unit = unit;
    }
}
