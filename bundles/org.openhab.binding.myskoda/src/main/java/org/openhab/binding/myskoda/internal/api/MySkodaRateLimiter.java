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
package org.openhab.binding.myskoda.internal.api;

import java.time.Instant;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpFields;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaRateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MySkodaRateLimiter} tracks the shared 20 requests/hour quota of a MySkoda API key,
 * based on the {@code RateLimit-Remaining}/{@code RateLimit-Reset} response headers, and blocks
 * further calls locally once the backend reports {@code 429 Too Many Requests} rather than
 * repeatedly hitting the backend. One instance is shared by an account bridge and all vehicle
 * things below it, since the quota itself is shared per API key.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaRateLimiter {

    private final Logger logger = LoggerFactory.getLogger(MySkodaRateLimiter.class);

    private @Nullable Instant blockedUntil;

    public synchronized void checkAllowed() throws MySkodaRateLimitException {
        Instant until = blockedUntil;
        if (until != null && Instant.now().isBefore(until)) {
            throw new MySkodaRateLimitException("MySkoda API rate limit in effect until " + until, until);
        }
    }

    /**
     * Update local quota tracking from a successful response's rate-limit headers.
     */
    public synchronized void onResponse(HttpFields headers) {
        String remainingHeader = headers.get("RateLimit-Remaining");
        String resetHeader = headers.get("RateLimit-Reset");
        if (remainingHeader == null || resetHeader == null) {
            return;
        }
        try {
            int remaining = Integer.parseInt(remainingHeader.trim());
            long resetSeconds = Long.parseLong(resetHeader.trim());
            if (remaining <= 0) {
                blockedUntil = Instant.now().plusSeconds(resetSeconds);
                logger.debug("MySkoda API quota exhausted, blocked until {}", blockedUntil);
            }
        } catch (NumberFormatException e) {
            logger.debug("Could not parse rate-limit headers 'RateLimit-Remaining={}' 'RateLimit-Reset={}'",
                    remainingHeader, resetHeader);
        }
    }

    /**
     * Record a {@code 429 Too Many Requests} response and block further calls until the
     * {@code Retry-After} it carries has elapsed.
     */
    public synchronized void onRateLimited(HttpFields headers) {
        String retryAfterHeader = headers.get("Retry-After");
        Instant until = Instant.now().plusSeconds(3600);
        if (retryAfterHeader != null) {
            try {
                until = Instant.now().plusSeconds(Long.parseLong(retryAfterHeader.trim()));
            } catch (NumberFormatException e) {
                logger.debug("Could not parse Retry-After header '{}'", retryAfterHeader);
            }
        }
        blockedUntil = until;
        logger.warn("MySkoda API rate limit exceeded, blocked until {}", until);
    }
}
