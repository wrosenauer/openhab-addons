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

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The {@link PendingProfileChanges} remembers charging profile settings changed through openHAB
 * until the vehicle reports them. The vehicle applies a profile update asynchronously, so for a
 * while after a change the API still reports the previous value. Without this, a second change
 * made in that window would be based on the stale profile and silently revert the first one.
 * A pending change ends when the vehicle reports its value, or after {@link #MAX_AGE} at the
 * latest, so a change the vehicle rejected (or one overridden in the MyŠkoda app) does not stick.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
class PendingProfileChanges {

    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private record Change(long profileId, String[] path, JsonElement value, Instant expires) {
    }

    private final Map<String, Change> changes = new ConcurrentHashMap<>();

    void add(long profileId, JsonElement value, String... path) {
        changes.put(profileId + "/" + String.join("/", path),
                new Change(profileId, path, value, Instant.now().plus(MAX_AGE)));
    }

    /**
     * Apply the pending changes to a profile read from the API, dropping those the vehicle already
     * reports and those that have expired.
     *
     * @return the profile with all remaining pending changes applied (a copy if any applies)
     */
    JsonObject applyTo(long profileId, JsonObject profile) {
        JsonObject result = profile;
        Instant now = Instant.now();
        for (Iterator<Change> it = changes.values().iterator(); it.hasNext();) {
            Change change = it.next();
            if (now.isAfter(change.expires())) {
                it.remove();
            } else if (change.profileId() == profileId) {
                if (change.value().equals(ChargingProfileSupport.getSetting(profile, change.path()))) {
                    it.remove();
                } else {
                    result = ChargingProfileSupport.withSetting(result, change.value(), change.path());
                }
            }
        }
        return result;
    }
}
