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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.myskoda.internal.api.dto.ProblemDetail;
import org.openhab.binding.myskoda.internal.api.dto.StartAirConditioningConfiguration;
import org.openhab.binding.myskoda.internal.api.dto.StartAuxiliaryHeatingConfiguration;
import org.openhab.binding.myskoda.internal.api.dto.TargetTemperature;
import org.openhab.binding.myskoda.internal.api.dto.VehicleResponse;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaApiException;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaAuthException;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaRateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * The {@link MySkodaApiClient} talks to the MySkoda public API
 * ({@code https://public.api.connect.skoda-auto.cz}), authenticating with the per-vehicle
 * {@code X-API-Key} header. One instance is owned by the account bridge and shared by all
 * vehicle things below it, so that the {@link MySkodaRateLimiter} sees every call made with that
 * key.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaApiClient {

    private static final String BASE_URL = "https://public.api.connect.skoda-auto.cz";
    private static final int REQUEST_TIMEOUT_MS = 10_000;

    private final Logger logger = LoggerFactory.getLogger(MySkodaApiClient.class);
    private final HttpClient httpClient;
    private final MySkodaRateLimiter rateLimiter;
    private final Gson gson = new Gson();

    private String apiKey;
    private @Nullable String apiKeyExpiresAt;

    public MySkodaApiClient(HttpClient httpClient, MySkodaRateLimiter rateLimiter, String apiKey) {
        this.httpClient = httpClient;
        this.rateLimiter = rateLimiter;
        this.apiKey = apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * @return the value of the last seen {@code X-API-Key-Expires-At} response header, or null if
     *         no call has succeeded yet.
     */
    public @Nullable String getApiKeyExpiresAt() {
        return apiKeyExpiresAt;
    }

    public VehicleResponse getVehicle(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        ContentResponse response = execute(HttpMethod.GET, "/api/v1/vehicles/" + vin, null);
        try {
            VehicleResponse vehicleResponse = gson.fromJson(response.getContentAsString(), VehicleResponse.class);
            return vehicleResponse == null ? new VehicleResponse() : vehicleResponse;
        } catch (JsonParseException e) {
            throw new MySkodaApiException("Could not parse vehicle response", e);
        }
    }

    public void startCharging(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, "/api/v1/vehicles/" + vin + "/charging/start", null);
    }

    public void stopCharging(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, "/api/v1/vehicles/" + vin + "/charging/stop", null);
    }

    public void startAirConditioning(String vin, @Nullable TargetTemperature targetTemperature,
            boolean airConditioningWithoutExternalPower)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        StartAirConditioningConfiguration body = new StartAirConditioningConfiguration(targetTemperature,
                airConditioningWithoutExternalPower);
        execute(HttpMethod.POST, "/api/v1/vehicles/" + vin + "/air-conditioning/start", gson.toJson(body));
    }

    public void stopAirConditioning(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, "/api/v1/vehicles/" + vin + "/air-conditioning/stop", null);
    }

    public void startAuxiliaryHeating(String vin, String spin, int durationInSeconds, String startMode,
            @Nullable TargetTemperature targetTemperature)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        StartAuxiliaryHeatingConfiguration body = new StartAuxiliaryHeatingConfiguration(spin, durationInSeconds,
                startMode);
        body.targetTemperature = targetTemperature;
        execute(HttpMethod.POST, "/api/v1/vehicles/" + vin + "/auxiliary-heating/start", gson.toJson(body));
    }

    public void stopAuxiliaryHeating(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, "/api/v1/vehicles/" + vin + "/auxiliary-heating/stop", null);
    }

    public void startActiveVentilation(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, "/api/v1/vehicles/" + vin + "/active-ventilation/start", null);
    }

    public void stopActiveVentilation(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, "/api/v1/vehicles/" + vin + "/active-ventilation/stop", null);
    }

    private ContentResponse execute(HttpMethod method, String path, @Nullable String jsonBody)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        rateLimiter.checkAllowed();

        Request request = httpClient.newRequest(BASE_URL + path).method(method)
                .timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).header("X-API-Key", apiKey)
                .header("Accept", "application/json");
        if (jsonBody != null) {
            request = request.header("Content-Type", "application/json")
                    .content(new StringContentProvider(jsonBody, "utf-8"));
        }

        ContentResponse response;
        try {
            response = request.send();
        } catch (TimeoutException | ExecutionException e) {
            throw new MySkodaApiException("MySkoda API request failed: " + e.getMessage(), e);
        }

        String expiresAt = response.getHeaders().get("X-API-Key-Expires-At");
        if (expiresAt != null) {
            apiKeyExpiresAt = expiresAt;
        }

        int status = response.getStatus();
        if (status == 200 || status == 202 || status == 204) {
            rateLimiter.onResponse(response.getHeaders());
            return response;
        }
        if (status == 401 || status == 403) {
            ProblemDetail problem = parseProblem(response);
            boolean expired = problem.type.endsWith("api-key-expired");
            throw new MySkodaAuthException(problem.detail.isBlank() ? problem.title : problem.detail, expired);
        }
        if (status == 429) {
            rateLimiter.onRateLimited(response.getHeaders());
            ProblemDetail problem = parseProblem(response);
            throw new MySkodaRateLimitException(problem.detail.isBlank() ? problem.title : problem.detail,
                    java.time.Instant.now().plusSeconds(3600));
        }
        ProblemDetail problem = parseProblem(response);
        String message = problem.detail.isBlank() ? ("HTTP " + status + " " + response.getReason()) : problem.detail;
        logger.debug("MySkoda API request {} {} failed: {}", method, path, message);
        throw new MySkodaApiException(message);
    }

    private ProblemDetail parseProblem(ContentResponse response) {
        try {
            ProblemDetail problem = gson.fromJson(response.getContentAsString(), ProblemDetail.class);
            return problem == null ? new ProblemDetail() : problem;
        } catch (JsonParseException e) {
            return new ProblemDetail();
        }
    }
}
