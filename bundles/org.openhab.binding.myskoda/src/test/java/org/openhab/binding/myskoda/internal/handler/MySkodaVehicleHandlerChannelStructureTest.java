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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openhab.binding.myskoda.internal.MySkodaBindingConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.myskoda.internal.MySkodaStateDescriptionProvider;
import org.openhab.binding.myskoda.internal.api.dto.FuelStatus;
import org.openhab.binding.myskoda.internal.api.dto.Vehicle;
import org.openhab.binding.myskoda.internal.api.dto.VehicleError;
import org.openhab.binding.myskoda.internal.api.dto.VehicleResponse;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelGroupTypeUID;

import com.google.gson.Gson;

/**
 * Tests how {@link MySkodaVehicleHandler} adapts the thing's channels to the data groups and
 * operations a vehicle supports.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
class MySkodaVehicleHandlerChannelStructureTest {

    private static final ThingUID THING_UID = new ThingUID(THING_TYPE_VEHICLE, "home", "mycar");
    private static final List<String> GROUPS = List.of(GROUP_STATUS, GROUP_ODOMETER, GROUP_FUEL, GROUP_POSITION,
            GROUP_CHARGING, GROUP_CLIMATE, GROUP_AUXILIARY_HEATING, GROUP_ACTIVE_VENTILATION, GROUP_CHARGING_PROFILE);

    private @Mock @NonNullByDefault({}) ThingHandlerCallback callback;
    private @Mock @NonNullByDefault({}) MySkodaStateDescriptionProvider stateDescriptionProvider;

    private @NonNullByDefault({}) MySkodaVehicleHandler handler;
    private @NonNullByDefault({}) VehicleResponse response;

    @BeforeEach
    void setUp() throws IOException {
        // a "last-updated" channel per group, plus the start/stop switches
        List<Channel> channels = new ArrayList<>();
        GROUPS.forEach(group -> channels.add(channel(group, CHANNEL_LAST_UPDATED)));
        channels.add(channel(GROUP_CHARGING, CHANNEL_CHARGING));
        channels.add(channel(GROUP_CLIMATE, CHANNEL_AIR_CONDITIONING));
        channels.add(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_HEATING));
        channels.add(channel(GROUP_ACTIVE_VENTILATION, CHANNEL_ACTIVE_VENTILATION));
        Thing thing = ThingBuilder.create(THING_TYPE_VEHICLE, THING_UID).withChannels(channels).build();

        when(callback.createChannelBuilder(any(ChannelUID.class), any()))
                .thenAnswer(invocation -> ChannelBuilder.create(invocation.getArgument(0, ChannelUID.class), "Switch"));
        when(callback.createChannelBuilders(any(ChannelGroupUID.class), any(ChannelGroupTypeUID.class)))
                .thenAnswer(invocation -> List.of(ChannelBuilder.create(
                        new ChannelUID(invocation.getArgument(0, ChannelGroupUID.class), CHANNEL_LAST_UPDATED),
                        "DateTime")));

        handler = new MySkodaVehicleHandler(thing, stateDescriptionProvider);
        handler.setCallback(callback);

        try (InputStream inputStream = VehicleResponse.class.getResourceAsStream("vehicle-bev.json")) {
            response = new Gson().fromJson(
                    new String(Objects.requireNonNull(inputStream).readAllBytes(), StandardCharsets.UTF_8),
                    VehicleResponse.class);
        }
    }

    @Test
    void removesUnsupportedGroupsAndControls() {
        handler.updateChannelStructure(vehicle(), response.errors);

        // BEV fixture: fuel reported as unsupported, auxiliary heating and active ventilation absent
        assertThat(groups(), is(Set.of(GROUP_STATUS, GROUP_ODOMETER, GROUP_POSITION, GROUP_CHARGING, GROUP_CLIMATE,
                GROUP_CHARGING_PROFILE)));
        assertThat(channelUIDs(), hasItem(uid(GROUP_CHARGING, CHANNEL_CHARGING)));
        assertThat(channelUIDs(), hasItem(uid(GROUP_CLIMATE, CHANNEL_AIR_CONDITIONING)));
        // setChargeMode is not in the fixture's operations, setChargingLimit is
        verify(stateDescriptionProvider).setReadOnly(uid(GROUP_CHARGING, CHANNEL_PREFERRED_CHARGE_MODE), true);
        verify(stateDescriptionProvider).setReadOnly(uid(GROUP_CHARGING, CHANNEL_TARGET_STATE_OF_CHARGE), false);
    }

    @Test
    void removesControlOfUnsupportedOperationInSupportedGroup() {
        Vehicle vehicle = vehicle();
        vehicle.operations = List.of();

        handler.updateChannelStructure(vehicle, response.errors);

        assertThat(groups(), hasItem(GROUP_CHARGING));
        assertThat(channelUIDs(), not(hasItem(uid(GROUP_CHARGING, CHANNEL_CHARGING))));
    }

    @Test
    void keepsTemporarilyMissingGroupsAndRestoresSupportedOnes() {
        handler.updateChannelStructure(vehicle(), response.errors);

        // fuel data shows up, climate is temporarily unavailable, odometer disabled
        Vehicle vehicle = vehicle();
        vehicle.fuelStatus = new FuelStatus();
        vehicle.airConditioning = null;
        vehicle.odometer = null;
        handler.updateChannelStructure(vehicle, List.of(error("AIR_CONDITIONING_UNAVAILABLE"),
                error("ODOMETER_DISABLED"), error("CHARGING_PROFILES_UNAVAILABLE")));

        assertThat(groups(), hasItem(GROUP_FUEL));
        assertThat(groups(), hasItem(GROUP_CLIMATE));
        assertThat(groups(), hasItem(GROUP_ODOMETER));
        assertThat(groups(), hasItem(GROUP_CHARGING_PROFILE));
    }

    @Test
    void chargingProfilesErrorDoesNotProtectChargingGroup() {
        Vehicle vehicle = vehicle();
        vehicle.charging = null;

        handler.updateChannelStructure(vehicle, List.of(error("CHARGING_PROFILES_UNAVAILABLE")));

        assertThat(groups(), not(hasItem(GROUP_CHARGING)));
        assertThat(groups(), hasItem(GROUP_CHARGING_PROFILE));
    }

    private Vehicle vehicle() {
        return Objects.requireNonNull(response.vehicle);
    }

    private static VehicleError error(String type) {
        VehicleError error = new VehicleError();
        error.type = type;
        return error;
    }

    private Set<ChannelUID> channelUIDs() {
        return handler.getThing().getChannels().stream().map(Channel::getUID).collect(Collectors.toSet());
    }

    private Set<String> groups() {
        return channelUIDs().stream().map(ChannelUID::getGroupId).filter(Objects::nonNull).map(Objects::requireNonNull)
                .collect(Collectors.toSet());
    }

    private static ChannelUID uid(String group, String id) {
        return new ChannelUID(THING_UID, group, id);
    }

    private static Channel channel(String group, String id) {
        return ChannelBuilder.create(uid(group, id), "String").build();
    }
}
