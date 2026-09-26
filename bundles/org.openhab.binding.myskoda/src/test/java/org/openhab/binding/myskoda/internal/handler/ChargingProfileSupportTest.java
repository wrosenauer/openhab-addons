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
package org.openhab.binding.myskoda.internal.handler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.myskoda.internal.api.dto.ChargingProfiles;
import org.openhab.binding.myskoda.internal.api.dto.VehicleResponse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Tests for {@link ChargingProfileSupport}.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
class ChargingProfileSupportTest {

    private @NonNullByDefault({}) ChargingProfiles profiles;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream inputStream = VehicleResponse.class.getResourceAsStream("vehicle-bev.json")) {
            if (inputStream == null) {
                throw new IOException("Fixture not found");
            }
            VehicleResponse response = new Gson()
                    .fromJson(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), VehicleResponse.class);
            profiles = response.vehicle.chargingProfiles;
        }
    }

    @Test
    void selectsProfileOfCurrentLocationByDefault() {
        JsonObject profile = ChargingProfileSupport.select(profiles, "");

        assertThat(profile, notNullValue());
        assertThat(profile.get("name").getAsString(), is("HOME"));
    }

    @Test
    void selectsConfiguredProfileByNameOrId() {
        assertThat(ChargingProfileSupport.select(profiles, "work").get("id").getAsLong(), is(654321L));
        assertThat(ChargingProfileSupport.select(profiles, " 654321 ").get("name").getAsString(), is("Work"));
        assertThat(ChargingProfileSupport.select(profiles, "Holiday"), nullValue());
    }

    @Test
    void selectsNothingAwayFromSavedLocations() {
        profiles.currentVehiclePositionProfile = null;

        assertThat(ChargingProfileSupport.select(profiles, ""), nullValue());
    }

    @Test
    void changesOneSettingAndKeepsEverythingElse() {
        JsonObject profile = selectExisting("HOME");

        JsonObject updated = ChargingProfileSupport.withSetting(profile, new JsonPrimitive(90),
                "targetStateOfChargeInPercent");

        assertThat(updated.getAsJsonObject("settings").get("targetStateOfChargeInPercent").getAsInt(), is(90));
        assertThat(updated.getAsJsonObject("settings").get("maxChargingCurrent").getAsString(), is("MAXIMUM"));
        assertThat(updated.get("timers"), is(profile.get("timers")));
        assertThat(updated.get("preferredChargingTimes"), is(profile.get("preferredChargingTimes")));
        // the cached profile is not modified
        assertThat(profile.getAsJsonObject("settings").get("targetStateOfChargeInPercent").getAsInt(), is(80));
    }

    @Test
    void createsMissingNestedSettings() {
        JsonObject profile = selectExisting("Work");

        JsonObject updated = ChargingProfileSupport.withSetting(profile, new JsonPrimitive(true),
                "minBatteryStateOfCharge", "enabled");

        assertThat(updated.getAsJsonObject("settings").getAsJsonObject("minBatteryStateOfCharge").get("enabled")
                .getAsBoolean(), is(true));
        assertThat(updated.getAsJsonObject("settings").get("targetStateOfChargeInPercent").getAsInt(), is(60));
    }

    private JsonObject selectExisting(String configured) {
        return Objects.requireNonNull(ChargingProfileSupport.select(profiles, configured));
    }
}
