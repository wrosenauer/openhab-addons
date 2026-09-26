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

/**
 * The {@link StartParameter} holds a value that the MySkoda public API only accepts as part of a
 * start command, e.g. the air conditioning target temperature. The vehicle reports the value it
 * last used on every poll, but a value set through a channel is kept as pending until it has been
 * sent with a start command - otherwise a poll in between would silently revert it to the
 * vehicle's previous value.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
class StartParameter<T> {

    private T vehicleValue;
    private @Nullable T pendingValue;

    StartParameter(T defaultValue) {
        this.vehicleValue = defaultValue;
    }

    /**
     * Record the value reported by the vehicle.
     *
     * @return the value the channel should show - the pending value if there is one
     */
    synchronized T updateFromVehicle(T value) {
        vehicleValue = value;
        T pending = pendingValue;
        return pending != null ? pending : value;
    }

    /**
     * Set a value to be sent with the next start command.
     */
    synchronized void set(T value) {
        pendingValue = value;
    }

    /**
     * @return the value to send with a start command
     */
    synchronized T get() {
        T pending = pendingValue;
        return pending != null ? pending : vehicleValue;
    }

    /**
     * Mark the pending value as sent, so the next poll's value is shown again.
     */
    synchronized void sent() {
        T pending = pendingValue;
        if (pending != null) {
            vehicleValue = pending;
            pendingValue = null;
        }
    }
}
