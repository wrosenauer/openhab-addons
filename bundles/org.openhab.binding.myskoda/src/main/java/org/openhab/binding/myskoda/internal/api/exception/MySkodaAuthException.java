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
package org.openhab.binding.myskoda.internal.api.exception;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link MySkodaAuthException} is thrown when the API key is missing, expired
 * (HTTP 401 {@code api-key-expired}) or not authorized for the requested vehicle
 * (HTTP 403 {@code api-key-not-authorized} / {@code operation-not-authorized}).
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaAuthException extends MySkodaApiException {

    private static final long serialVersionUID = 1L;

    private final boolean expired;

    public MySkodaAuthException(String message, boolean expired) {
        super(message);
        this.expired = expired;
    }

    /**
     * @return true if the key is expired (HTTP 401), false if it is simply not authorized (HTTP 403)
     */
    public boolean isExpired() {
        return expired;
    }
}
