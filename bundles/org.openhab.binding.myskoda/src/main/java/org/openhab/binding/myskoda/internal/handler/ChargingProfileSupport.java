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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.myskoda.internal.api.dto.ChargingProfile;
import org.openhab.binding.myskoda.internal.api.dto.ChargingProfiles;
import org.openhab.binding.myskoda.internal.api.dto.CurrentVehiclePositionProfile;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * The {@link ChargingProfileSupport} selects the charging profile a vehicle thing shows and
 * changes single settings of it. Changes are applied to a copy of the raw profile JSON, so all
 * other fields - timers, preferred charging times and fields this binding does not know - are
 * sent back unchanged.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
final class ChargingProfileSupport {

    private static final Gson GSON = new Gson();

    private ChargingProfileSupport() {
    }

    /**
     * Select the charging profile to show.
     *
     * @param profiles the vehicle's charging profiles
     * @param configured the profile configured on the thing, by name (case-insensitive) or id; blank
     *            for the profile of the location the vehicle is currently at
     * @return the raw profile, or null if there is no matching profile
     */
    static @Nullable JsonObject select(ChargingProfiles profiles, String configured) {
        String wanted = configured.trim();
        if (wanted.isEmpty()) {
            CurrentVehiclePositionProfile current = profiles.currentVehiclePositionProfile;
            if (current == null) {
                return null;
            }
            wanted = String.valueOf(current.id);
        }
        for (JsonObject profile : profiles.profiles) {
            ChargingProfile parsed = parse(profile);
            if (parsed != null && (String.valueOf(parsed.id).equals(wanted) || parsed.name.equalsIgnoreCase(wanted))) {
                return profile;
            }
        }
        return null;
    }

    static @Nullable ChargingProfile parse(JsonObject profile) {
        try {
            return GSON.fromJson(profile, ChargingProfile.class);
        } catch (JsonParseException e) {
            return null;
        }
    }

    /**
     * @param profile the raw profile
     * @param value the new value
     * @param path the path of the setting below the profile's {@code settings} object, e.g.
     *            {@code "minBatteryStateOfCharge", "enabled"}
     * @return a copy of the profile with the setting changed; missing intermediate objects are created
     */
    static JsonObject withSetting(JsonObject profile, JsonElement value, String... path) {
        JsonObject copy = profile.deepCopy();
        JsonObject parent = childObject(copy, "settings");
        for (int i = 0; i < path.length - 1; i++) {
            parent = childObject(parent, path[i]);
        }
        parent.add(path[path.length - 1], value);
        return copy;
    }

    private static JsonObject childObject(JsonObject parent, String name) {
        JsonElement child = parent.get(name);
        if (child instanceof JsonObject object) {
            return object;
        }
        JsonObject object = new JsonObject();
        parent.add(name, object);
        return object;
    }
}
