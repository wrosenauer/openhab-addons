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

import static org.openhab.binding.myskoda.internal.MySkodaBindingConstants.PROPERTY_API_KEY_EXPIRES_AT;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.myskoda.internal.api.MySkodaApiClient;
import org.openhab.binding.myskoda.internal.api.MySkodaRateLimiter;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaAuthException;
import org.openhab.binding.myskoda.internal.config.MySkodaAccountConfiguration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;

/**
 * The {@link MySkodaAccountHandler} is the Bridge handler for a MySkoda API key. One key can
 * cover several vehicles, so it owns the single {@link MySkodaApiClient}/{@link MySkodaRateLimiter}
 * pair shared by all {@link MySkodaVehicleHandler}s below it - the 20 requests/hour quota is
 * tracked per key, not per vehicle.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaAccountHandler extends BaseBridgeHandler {

    private final HttpClient httpClient;
    private final MySkodaRateLimiter rateLimiter = new MySkodaRateLimiter();
    private @Nullable MySkodaApiClient apiClient;

    public MySkodaAccountHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        MySkodaAccountConfiguration config = getConfigAs(MySkodaAccountConfiguration.class);
        if (config.apiKey.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/myskoda.account.no-api-key");
            return;
        }
        apiClient = new MySkodaApiClient(httpClient, rateLimiter, config.apiKey);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // the account bridge has no channels of its own
    }

    public @Nullable MySkodaApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Called by a {@link MySkodaVehicleHandler} after a successful API call to surface the
     * {@code X-API-Key-Expires-At} value as a bridge property, since the public API has no
     * refresh-token mechanism - the user must regenerate the key in the MySkoda app before it
     * expires.
     */
    public void refreshApiKeyExpiryProperty() {
        MySkodaApiClient client = apiClient;
        String expiresAt = client == null ? null : client.getApiKeyExpiresAt();
        if (expiresAt != null) {
            updateProperty(PROPERTY_API_KEY_EXPIRES_AT, expiresAt);
        }
    }

    /**
     * Called by a {@link MySkodaVehicleHandler} when the API key is rejected as expired.
     */
    public void reportAuthError(MySkodaAuthException e) {
        if (e.isExpired()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/myskoda.account.api-key-expired");
        }
    }
}
