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
 * The {@link Vehicle} dto describes a vehicle and its current state.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class Vehicle {

    public String vin = "";
    public String name = "";
    public String licensePlate = "";
    public String renderUrl = "";
    public @Nullable VehicleStatus status;
    public @Nullable FuelStatus fuelStatus;
    public @Nullable Odometer odometer;
    public @Nullable ParkingPosition parkingPosition;
    public @Nullable AirConditioning airConditioning;
    public @Nullable AuxiliaryHeating auxiliaryHeating;
    public @Nullable ActiveVentilation activeVentilation;
    public @Nullable Charging charging;
}
