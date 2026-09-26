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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.myskoda.internal.api.dto.VehicleResponse;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaApiException;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaAuthException;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaRateLimitException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    private static final String OTHER_VIN = "TMBJB9NY5RF888888";

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

        client = new MySkodaApiClient(httpClientMock, "test-api-key");
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

        Instant before = Instant.now();
        MySkodaRateLimitException exception = org.junit.jupiter.api.Assertions
                .assertThrows(MySkodaRateLimitException.class, () -> client.getVehicle(VIN));

        // the retry time comes from the Retry-After header, not a fixed hour
        assertThat(exception.getRetryAfter().isBefore(before.plusSeconds(120)), is(true));
        assertThat(exception.getRetryAfter().isAfter(before.plusSeconds(30)), is(true));
    }

    @Test
    void rateLimitIsTrackedPerVin() throws Exception {
        when(contentResponseMock.getStatus()).thenReturn(429);
        when(contentResponseMock.getContentAsString()).thenReturn(
                "{\"type\":\"https://public.api.connect.skoda-auto.cz/problems/rate-limit-exceeded\",\"status\":429}");
        HttpFields headers = new HttpFields();
        headers.add("Retry-After", "600");
        when(contentResponseMock.getHeaders()).thenReturn(headers);
        org.junit.jupiter.api.Assertions.assertThrows(MySkodaRateLimitException.class, () -> client.getVehicle(VIN));

        // the first VIN is now blocked locally, without another request ...
        org.junit.jupiter.api.Assertions.assertThrows(MySkodaRateLimitException.class, () -> client.getVehicle(VIN));
        verify(httpClientMock, times(1)).newRequest(anyString());

        // ... while another vehicle using the same key is not affected
        when(contentResponseMock.getStatus()).thenReturn(200);
        when(contentResponseMock.getContentAsString()).thenReturn("{\"vehicle\":{\"vin\":\"" + OTHER_VIN + "\"}}");
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());
        assertThat(client.getVehicle(OTHER_VIN).vehicle.vin, is(OTHER_VIN));
    }

    @Test
    void vehicleNotAcceptingRequestsIsNoRateLimit() throws Exception {
        when(contentResponseMock.getStatus()).thenReturn(429);
        when(contentResponseMock.getContentAsString()).thenReturn(
                "{\"type\":\"https://public.api.connect.skoda-auto.cz/problems/vehicle-not-accepting-requests\",\"status\":429,\"detail\":\"try later\"}");
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());

        MySkodaApiException exception = org.junit.jupiter.api.Assertions.assertThrows(MySkodaApiException.class,
                () -> client.startCharging(VIN));
        assertThat(exception instanceof MySkodaRateLimitException, is(false));

        // no local block - the next call goes out
        when(contentResponseMock.getStatus()).thenReturn(202);
        client.startCharging(VIN);
        verify(httpClientMock, times(2)).newRequest(anyString());
    }

    @Test
    void operationNotAuthorizedIsNoAuthError() {
        when(contentResponseMock.getStatus()).thenReturn(403);
        when(contentResponseMock.getContentAsString()).thenReturn(
                "{\"type\":\"https://public.api.connect.skoda-auto.cz/problems/operation-not-authorized\",\"status\":403,\"detail\":\"refused\"}");
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());

        MySkodaApiException exception = org.junit.jupiter.api.Assertions.assertThrows(MySkodaApiException.class,
                () -> client.startCharging(VIN));

        assertThat(exception instanceof MySkodaAuthException, is(false));
        assertThat(exception.getMessage(), is("refused"));
    }

    @Test
    void setChargingLimitSendsPutWithTargetStateOfCharge() throws Exception {
        when(contentResponseMock.getStatus()).thenReturn(202);
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());

        client.setChargingLimit(VIN, 80);

        verify(httpClientMock)
                .newRequest("https://public.api.connect.skoda-auto.cz/api/v1/vehicles/" + VIN + "/charging/limit");
        verify(requestMock).method(HttpMethod.PUT);
        assertThat(sentBody(), is("{\"targetStateOfChargeInPercent\":80}"));
    }

    @Test
    void setChargeModeSendsPutWithChargeMode() throws Exception {
        when(contentResponseMock.getStatus()).thenReturn(202);
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());

        client.setChargeMode(VIN, "TIMER");

        verify(httpClientMock)
                .newRequest("https://public.api.connect.skoda-auto.cz/api/v1/vehicles/" + VIN + "/charging/mode");
        verify(requestMock).method(HttpMethod.PUT);
        assertThat(sentBody(), is("{\"chargeMode\":\"TIMER\"}"));
    }

    @Test
    void rejectedParameterReportsAllowedValues() {
        when(contentResponseMock.getStatus()).thenReturn(400);
        when(contentResponseMock.getContentAsString()).thenReturn(
                "{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"Invalid charging limit.\",\"parameter\":\"targetStateOfChargeInPercent\",\"rejectedValue\":55,\"allowedValues\":[50,60,70,80,90,100]}");
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());

        MySkodaApiException exception = org.junit.jupiter.api.Assertions.assertThrows(MySkodaApiException.class,
                () -> client.setChargingLimit(VIN, 55));

        assertThat(exception.getMessage(),
                is("Invalid charging limit. (targetStateOfChargeInPercent must be one of [50,60,70,80,90,100])"));
    }

    @Test
    void updateChargingProfileSendsCompleteProfile() throws Exception {
        when(contentResponseMock.getStatus()).thenReturn(202);
        when(contentResponseMock.getHeaders()).thenReturn(new HttpFields());
        JsonObject profile = JsonParser
                .parseString("{\"id\":123,\"name\":\"HOME\",\"settings\":{},\"timers\":[{\"id\":1}]}")
                .getAsJsonObject();

        client.updateChargingProfile(VIN, 123, profile);

        verify(httpClientMock).newRequest(
                "https://public.api.connect.skoda-auto.cz/api/v1/vehicles/" + VIN + "/charging-profiles/123");
        verify(requestMock).method(HttpMethod.PUT);
        assertThat(sentBody(), is(profile.toString()));
    }

    private String sentBody() {
        ArgumentCaptor<ContentProvider> captor = ArgumentCaptor.forClass(ContentProvider.class);
        verify(requestMock).content(captor.capture());
        StringBuilder body = new StringBuilder();
        for (ByteBuffer buffer : captor.getValue()) {
            body.append(StandardCharsets.UTF_8.decode(buffer));
        }
        return body.toString();
    }
}
