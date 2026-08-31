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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/**
 * Tests that a {@link VehicleResponse} as returned by {@code GET /api/v1/vehicles/{vin}} is
 * correctly deserialized from JSON matching the MySkoda public API's OpenAPI schema.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
class VehicleResponseDeserializationTest {

    private final Gson gson = new Gson();

    @Test
    void deserializesBevVehicleResponse() throws IOException {
        VehicleResponse response = gson.fromJson(readFixture("vehicle-bev.json"), VehicleResponse.class);

        assertThat(response, notNullValue());
        Vehicle vehicle = response.vehicle;
        assertThat(vehicle, notNullValue());
        assertThat(vehicle.vin, is("TMBJB9NY5RF999999"));
        assertThat(vehicle.name, is("My Enyaq"));

        assertThat(vehicle.status, notNullValue());
        OverallVehicleStatus overall = vehicle.status.overall;
        assertThat(overall, notNullValue());
        assertThat(overall.doorsLocked, is("YES"));
        assertThat(overall.locked, is("YES"));

        assertThat(vehicle.odometer, notNullValue());
        assertThat(vehicle.odometer.mileageInKm, is(12753L));

        assertThat(vehicle.parkingPosition, notNullValue());
        assertThat(vehicle.parkingPosition.gpsCoordinates, notNullValue());
        assertThat(vehicle.parkingPosition.gpsCoordinates.latitude, is(37.4224428));

        assertThat(vehicle.charging, notNullValue());
        assertThat(vehicle.charging.status, notNullValue());
        assertThat(vehicle.charging.status.state, is("CHARGING"));
        assertThat(vehicle.charging.status.battery, notNullValue());
        assertThat(vehicle.charging.status.battery.stateOfChargeInPercent, is(71));
        assertThat(vehicle.charging.settings, notNullValue());
        assertThat(vehicle.charging.settings.targetStateOfChargeInPercent, is(80));

        assertThat(vehicle.airConditioning, notNullValue());
        assertThat(vehicle.airConditioning.state, is("HEATING"));
        assertThat(vehicle.airConditioning.targetTemperature, notNullValue());
        assertThat(vehicle.airConditioning.targetTemperature.value, is(22.5));
        assertThat(vehicle.airConditioning.windowHeating, notNullValue());
        assertThat(vehicle.airConditioning.windowHeating.front, is("ON"));

        // a BEV does not report fuelStatus - only an UNSUPPORTED error for it
        assertThat(vehicle.fuelStatus, is((FuelStatus) null));
        assertThat(response.errors, notNullValue());
        assertThat(response.errors.size(), is(1));
        assertThat(response.errors.get(0).type, is("FUEL_STATUS_UNSUPPORTED"));
    }

    private String readFixture(String name) throws IOException {
        try (InputStream inputStream = VehicleResponseDeserializationTest.class.getResourceAsStream(name)) {
            if (inputStream == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
