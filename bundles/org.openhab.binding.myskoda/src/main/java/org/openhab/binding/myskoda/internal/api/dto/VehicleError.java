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

/**
 * The {@link VehicleError} dto describes a part of the vehicle data that could not be retrieved,
 * is not supported (e.g. {@code CHARGING_UNSUPPORTED}), or is currently disabled.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class VehicleError {

    public String type = "";
    public String description = "";
}
