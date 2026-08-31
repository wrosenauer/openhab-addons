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
package org.openhab.binding.myskoda.internal.api.exception;

import java.time.Instant;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link MySkodaRateLimitException} is thrown when the shared 20 requests/hour quota for an
 * API key has been exhausted, either because the local
 * {@link org.openhab.binding.myskoda.internal.api.MySkodaRateLimiter}
 * pre-emptively blocked the call, or because the backend responded with HTTP 429.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaRateLimitException extends MySkodaApiException {

    private static final long serialVersionUID = 1L;

    private final Instant retryAfter;

    public MySkodaRateLimitException(String message, Instant retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Instant getRetryAfter() {
        return retryAfter;
    }
}
