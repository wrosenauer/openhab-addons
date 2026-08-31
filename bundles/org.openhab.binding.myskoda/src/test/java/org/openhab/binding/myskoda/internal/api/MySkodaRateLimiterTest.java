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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.http.HttpFields;
import org.junit.jupiter.api.Test;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaRateLimitException;

/**
 * Tests for {@link MySkodaRateLimiter}.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
class MySkodaRateLimiterTest {

    @Test
    void allowsCallsWhenQuotaRemains() {
        MySkodaRateLimiter limiter = new MySkodaRateLimiter();
        HttpFields headers = new HttpFields();
        headers.add("RateLimit-Remaining", "5");
        headers.add("RateLimit-Reset", "3600");

        limiter.onResponse(headers);

        assertDoesNotThrow(limiter::checkAllowed);
    }

    @Test
    void blocksFurtherCallsWhenQuotaExhausted() {
        MySkodaRateLimiter limiter = new MySkodaRateLimiter();
        HttpFields headers = new HttpFields();
        headers.add("RateLimit-Remaining", "0");
        headers.add("RateLimit-Reset", "3600");

        limiter.onResponse(headers);

        assertThrows(MySkodaRateLimitException.class, limiter::checkAllowed);
    }

    @Test
    void blocksFurtherCallsAfterRateLimitedResponse() {
        MySkodaRateLimiter limiter = new MySkodaRateLimiter();
        HttpFields headers = new HttpFields();
        headers.add("Retry-After", "120");

        limiter.onRateLimited(headers);

        assertThrows(MySkodaRateLimitException.class, limiter::checkAllowed);
    }

    @Test
    void ignoresUnparsableHeaders() {
        MySkodaRateLimiter limiter = new MySkodaRateLimiter();
        HttpFields headers = new HttpFields();
        headers.add("RateLimit-Remaining", "not-a-number");
        headers.add("RateLimit-Reset", "also-not-a-number");

        limiter.onResponse(headers);

        assertDoesNotThrow(limiter::checkAllowed);
    }
}
