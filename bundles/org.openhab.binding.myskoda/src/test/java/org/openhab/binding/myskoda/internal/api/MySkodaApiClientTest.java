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
package org.openhab.binding.myskoda.internal.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.myskoda.internal.api.dto.VehicleResponse;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaAuthException;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaRateLimitException;

/**
 * Tests for {@link MySkodaApiClient}, mocking the Jetty {@link HttpClient} so no network calls
 * are made.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
class MySkodaApiClientTest {

    private static final String VIN = "TMBJB9NY5RF999999";

    private @Mock @NonNullByDefault({}) HttpClient httpClientMock;
    private @Mock @NonNullByDefault({}) Request requestMock;
    private @Mock @NonNullByDefault({}) ContentResponse contentResponseMock;

    private @NonNullByDefault({}) MySkodaApiClient client;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException, TimeoutException {
        when(httpClientMock.newRequest(anyString())).thenReturn(requestMock);
        when(requestMock.method(any(HttpMethod.class))).thenReturn(requestMock);
        when(requestMock.timeout(anyLong(), any())).thenReturn(requestMock);
        when(requestMock.header(anyString(), anyString())).thenReturn(requestMock);
        when(requestMock.content(any())).thenReturn(requestMock);
        when(requestMock.send()).thenReturn(contentResponseMock);

        client = new MySkodaApiClient(httpClientMock, new MySkodaRateLimiter(), "test-api-key");
    }

    @Test
    void parsesSuccessfulVehicleResponse() throws Exception {
        when(contentResponseMock.getStatus()).thenReturn(200);
        when(contentResponseMock.getContentAsString())
                .thenReturn("{\"vehicle\":{\"vin\":\"" + VIN + "\",\"name\":\"My Car\"},\"errors\":[]}");
        HttpFields headers = new HttpFields();
        headers.add("RateLimit-Remaining", "19");
        headers.add("RateLimit-Reset", "3600");
        headers.add("X-API-Key-Expires-At", "2027-01-01T00:00:00Z");
        when(contentResponseMock.getHeaders()).thenReturn(headers);

        VehicleResponse response = client.getVehicle(VIN);

        assertThat(response.vehicle, is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.vehicle.vin, is(VIN));
        assertThat(client.getApiKeyExpiresAt(), is("2027-01-01T00:00:00Z"));
    }

    @Test
    void mapsExpiredApiKeyTo401() {
        when(contentResponseMock.getStatus()).thenReturn(401);
        when(contentResponseMock.getContentAsString()).thenReturn(
                "{\"type\":\"https://public.api.connect.skoda-auto.cz/problems/api-key-expired\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"expired\"}");
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());

        MySkodaAuthException exception = org.junit.jupiter.api.Assertions.assertThrows(MySkodaAuthException.class,
                () -> client.getVehicle(VIN));

        assertThat(exception.isExpired(), is(true));
    }

    @Test
    void mapsUnauthorizedApiKeyTo403AsNotExpired() {
        when(contentResponseMock.getStatus()).thenReturn(403);
        when(contentResponseMock.getContentAsString()).thenReturn(
                "{\"type\":\"https://public.api.connect.skoda-auto.cz/problems/api-key-not-authorized\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"not authorized\"}");
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());

        MySkodaAuthException exception = org.junit.jupiter.api.Assertions.assertThrows(MySkodaAuthException.class,
                () -> client.getVehicle(VIN));

        assertThat(exception.isExpired(), is(false));
    }

    @Test
    void mapsTooManyRequestsToRateLimitException() {
        when(contentResponseMock.getStatus()).thenReturn(429);
        when(contentResponseMock.getContentAsString()).thenReturn(
                "{\"type\":\"https://public.api.connect.skoda-auto.cz/problems/rate-limit-exceeded\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"quota exceeded\"}");
        HttpFields headers = new HttpFields();
        headers.add("Retry-After", "60");
        when(contentResponseMock.getHeaders()).thenReturn(headers);

        org.junit.jupiter.api.Assertions.assertThrows(MySkodaRateLimitException.class, () -> client.getVehicle(VIN));
    }
}
