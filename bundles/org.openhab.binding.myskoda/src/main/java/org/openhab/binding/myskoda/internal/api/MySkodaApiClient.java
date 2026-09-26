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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.openhab.binding.myskoda.internal.api.dto.ChargeMode;
import org.openhab.binding.myskoda.internal.api.dto.ChargingLimit;
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
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * The {@link MySkodaApiClient} talks to the MySkoda public API
 * ({@code https://public.api.connect.skoda-auto.cz}), authenticating with the
 * {@code X-API-Key} header. One instance is owned by the account bridge and shared by all
 * vehicle things below it; it keeps one {@link MySkodaRateLimiter} per VIN.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaApiClient {

    private static final String BASE_URL = "https://public.api.connect.skoda-auto.cz";
    private static final int REQUEST_TIMEOUT_MS = 10_000;

    private static final String PROBLEM_API_KEY_EXPIRED = "/api-key-expired";
    private static final String PROBLEM_OPERATION_NOT_AUTHORIZED = "/operation-not-authorized";
    private static final String PROBLEM_VEHICLE_NOT_ACCEPTING_REQUESTS = "/vehicle-not-accepting-requests";

    private final Logger logger = LoggerFactory.getLogger(MySkodaApiClient.class);
    private final HttpClient httpClient;
    private final Map<String, MySkodaRateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    private String apiKey;
    private @Nullable String apiKeyExpiresAt;

    public MySkodaApiClient(HttpClient httpClient, String apiKey) {
        this.httpClient = httpClient;
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

    /**
     * @param include the parts of the vehicle data to return (e.g. {@code chargingProfiles}); all parts
     *            the vehicle supports when empty
     */
    public VehicleResponse getVehicle(String vin, String... include)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        ContentResponse response = execute(HttpMethod.GET, vin,
                include.length == 0 ? "" : "?include=" + String.join(",", include), null);
        try {
            VehicleResponse vehicleResponse = gson.fromJson(response.getContentAsString(), VehicleResponse.class);
            return vehicleResponse == null ? new VehicleResponse() : vehicleResponse;
        } catch (JsonParseException e) {
            throw new MySkodaApiException("Could not parse vehicle response", e);
        }
    }

    public void startCharging(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, vin, "/charging/start", null);
    }

    public void stopCharging(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, vin, "/charging/stop", null);
    }

    public void setChargingLimit(String vin, int targetStateOfChargeInPercent)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.PUT, vin, "/charging/limit", gson.toJson(new ChargingLimit(targetStateOfChargeInPercent)));
    }

    public void setChargeMode(String vin, String chargeMode)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.PUT, vin, "/charging/mode", gson.toJson(new ChargeMode(chargeMode)));
    }

    /**
     * Replace a charging profile. The vehicle applies the submitted profile as a whole, so
     * {@code profile} must be complete, not just the changed fields.
     */
    public void updateChargingProfile(String vin, long profileId, JsonObject profile)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.PUT, vin, "/charging-profiles/" + profileId, profile.toString());
    }

    public void startAirConditioning(String vin, @Nullable TargetTemperature targetTemperature,
            boolean airConditioningWithoutExternalPower)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        StartAirConditioningConfiguration body = new StartAirConditioningConfiguration(targetTemperature,
                airConditioningWithoutExternalPower);
        execute(HttpMethod.POST, vin, "/air-conditioning/start", gson.toJson(body));
    }

    public void stopAirConditioning(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, vin, "/air-conditioning/stop", null);
    }

    public void startAuxiliaryHeating(String vin, String spin, int durationInSeconds, String startMode,
            @Nullable TargetTemperature targetTemperature)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        StartAuxiliaryHeatingConfiguration body = new StartAuxiliaryHeatingConfiguration(spin, durationInSeconds,
                startMode);
        body.targetTemperature = targetTemperature;
        execute(HttpMethod.POST, vin, "/auxiliary-heating/start", gson.toJson(body));
    }

    public void stopAuxiliaryHeating(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, vin, "/auxiliary-heating/stop", null);
    }

    public void startActiveVentilation(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, vin, "/active-ventilation/start", null);
    }

    public void stopActiveVentilation(String vin)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        execute(HttpMethod.POST, vin, "/active-ventilation/stop", null);
    }

    /**
     * Execute a request against {@code /api/v1/vehicles/{vin}{subPath}}. The quota is tracked per VIN, as
     * the API documentation states that requests are rate-limited per VIN.
     */
    private ContentResponse execute(HttpMethod method, String vin, String subPath, @Nullable String jsonBody)
            throws MySkodaApiException, MySkodaAuthException, MySkodaRateLimitException, InterruptedException {
        String path = "/api/v1/vehicles/" + vin + subPath;
        MySkodaRateLimiter rateLimiter = rateLimiters.computeIfAbsent(vin, v -> new MySkodaRateLimiter());
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
        ProblemDetail problem = parseProblem(response);
        String message = problem.toMessage("HTTP " + status + " " + response.getReason());
        // 401 and 403 responses do not consume quota, but every other error response does and carries
        // the RateLimit-* headers as well
        if (status == 401 || (status == 403 && !problem.type.endsWith(PROBLEM_OPERATION_NOT_AUTHORIZED))) {
            throw new MySkodaAuthException(message, problem.type.endsWith(PROBLEM_API_KEY_EXPIRED));
        }
        if (status == 429 && !problem.type.endsWith(PROBLEM_VEHICLE_NOT_ACCEPTING_REQUESTS)) {
            throw new MySkodaRateLimitException(message, rateLimiter.onRateLimited(response.getHeaders()));
        }
        rateLimiter.onResponse(response.getHeaders());
        // the vehicle refusing a single operation (operation-not-authorized, operation-not-supported,
        // operation-disabled, vehicle-not-accepting-requests) is not an API key or quota problem
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
