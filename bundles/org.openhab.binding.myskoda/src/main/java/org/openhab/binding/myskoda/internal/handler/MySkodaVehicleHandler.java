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

import static org.openhab.binding.myskoda.internal.MySkodaBindingConstants.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.myskoda.internal.MySkodaStateDescriptionProvider;
import org.openhab.binding.myskoda.internal.api.MySkodaApiClient;
import org.openhab.binding.myskoda.internal.api.dto.ActiveVentilation;
import org.openhab.binding.myskoda.internal.api.dto.AirConditioning;
import org.openhab.binding.myskoda.internal.api.dto.AuxiliaryHeating;
import org.openhab.binding.myskoda.internal.api.dto.Charging;
import org.openhab.binding.myskoda.internal.api.dto.ChargingProfile;
import org.openhab.binding.myskoda.internal.api.dto.ChargingProfileSettings;
import org.openhab.binding.myskoda.internal.api.dto.ChargingProfiles;
import org.openhab.binding.myskoda.internal.api.dto.ChargingSettings;
import org.openhab.binding.myskoda.internal.api.dto.ChargingStatus;
import org.openhab.binding.myskoda.internal.api.dto.CurrentVehiclePositionProfile;
import org.openhab.binding.myskoda.internal.api.dto.EngineRange;
import org.openhab.binding.myskoda.internal.api.dto.FuelStatus;
import org.openhab.binding.myskoda.internal.api.dto.GpsCoordinates;
import org.openhab.binding.myskoda.internal.api.dto.MinBatteryStateOfCharge;
import org.openhab.binding.myskoda.internal.api.dto.Odometer;
import org.openhab.binding.myskoda.internal.api.dto.OverallVehicleStatus;
import org.openhab.binding.myskoda.internal.api.dto.ParkingPosition;
import org.openhab.binding.myskoda.internal.api.dto.TargetTemperature;
import org.openhab.binding.myskoda.internal.api.dto.Vehicle;
import org.openhab.binding.myskoda.internal.api.dto.VehicleError;
import org.openhab.binding.myskoda.internal.api.dto.VehicleOperation;
import org.openhab.binding.myskoda.internal.api.dto.VehicleResponse;
import org.openhab.binding.myskoda.internal.api.dto.VehicleStatus;
import org.openhab.binding.myskoda.internal.api.dto.VehicleStatusDetail;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaApiException;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaAuthException;
import org.openhab.binding.myskoda.internal.api.exception.MySkodaRateLimitException;
import org.openhab.binding.myskoda.internal.config.MySkodaVehicleConfiguration;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.type.ChannelGroupTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The {@link MySkodaVehicleHandler} polls one vehicle's state from the MySkoda public API and
 * dispatches the commands the API supports: charging, air conditioning, auxiliary heating and
 * active ventilation start/stop, plus the charging limit and charge mode. Since the account
 * bridge's quota is only 20 requests/hour, a command does <b>not</b> trigger an immediate re-poll -
 * the new state is picked up on the next scheduled poll instead.
 * <p>
 * Parameters the API only accepts with a start command (target temperatures, auxiliary heating
 * duration and start mode, air conditioning without external power) are held as
 * {@link StartParameter}s and sent with the next start.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaVehicleHandler extends BaseThingHandler {

    private static final Set<String> AUXILIARY_START_MODES = Set.of("HEATING", "VENTILATION");

    /**
     * A channel that accepts commands, and the operation it needs. A control (a start/stop switch)
     * is removed from the thing when the vehicle supports neither of its operations; the other
     * channels also show a value and are made read-only instead. The channel type id of a control
     * equals its channel id.
     */
    private record CommandChannel(String group, String channel, String operation, @Nullable String stopOperation,
            boolean control) {

        boolean isSupportedBy(Set<String> supported) {
            String stop = stopOperation;
            return supported.contains(operation) || (stop != null && supported.contains(stop));
        }
    }

    /**
     * A channel group, the channel group type, and the part of the vehicle data that feeds it
     * (with the prefix of the errors the API reports for that part). The group is removed from the
     * thing while the vehicle does not support the part.
     */
    private record DataGroup(String group, String groupType, String errorPrefix,
            Function<Vehicle, @Nullable Object> part) {
    }

    private static final List<DataGroup> DATA_GROUPS = List.of(
            new DataGroup(GROUP_STATUS, "status-values", "VEHICLE_STATUS", vehicle -> vehicle.status),
            new DataGroup(GROUP_ODOMETER, "odometer-values", "ODOMETER", vehicle -> vehicle.odometer),
            new DataGroup(GROUP_FUEL, "fuel-values", "FUEL_STATUS", vehicle -> vehicle.fuelStatus),
            new DataGroup(GROUP_POSITION, "position-values", "PARKING_POSITION", vehicle -> vehicle.parkingPosition),
            new DataGroup(GROUP_CHARGING, "charging-values", "CHARGING", vehicle -> vehicle.charging),
            new DataGroup(GROUP_CLIMATE, "climate-values", "AIR_CONDITIONING", vehicle -> vehicle.airConditioning),
            new DataGroup(GROUP_AUXILIARY_HEATING, "auxiliary-heating-values", "AUXILIARY_HEATING",
                    vehicle -> vehicle.auxiliaryHeating),
            new DataGroup(GROUP_ACTIVE_VENTILATION, "active-ventilation-values", "ACTIVE_VENTILATION",
                    vehicle -> vehicle.activeVentilation),
            new DataGroup(GROUP_CHARGING_PROFILE, "charging-profile-values", "CHARGING_PROFILES",
                    vehicle -> vehicle.chargingProfiles));

    private static final List<CommandChannel> COMMAND_CHANNELS = List.of(
            new CommandChannel(GROUP_CHARGING, CHANNEL_CHARGING, OPERATION_START_CHARGING, OPERATION_STOP_CHARGING,
                    true),
            new CommandChannel(GROUP_CHARGING, CHANNEL_TARGET_STATE_OF_CHARGE, OPERATION_SET_CHARGING_LIMIT, null,
                    false),
            new CommandChannel(GROUP_CHARGING, CHANNEL_PREFERRED_CHARGE_MODE, OPERATION_SET_CHARGE_MODE, null, false),
            new CommandChannel(GROUP_CHARGING_PROFILE, CHANNEL_TARGET_STATE_OF_CHARGE,
                    OPERATION_UPDATE_CHARGING_PROFILE, null, false),
            new CommandChannel(GROUP_CHARGING_PROFILE, CHANNEL_MAX_CHARGE_CURRENT, OPERATION_UPDATE_CHARGING_PROFILE,
                    null, false),
            new CommandChannel(GROUP_CHARGING_PROFILE, CHANNEL_AUTO_UNLOCK_PLUG, OPERATION_UPDATE_CHARGING_PROFILE,
                    null, false),
            new CommandChannel(GROUP_CHARGING_PROFILE, CHANNEL_MIN_STATE_OF_CHARGE_ENABLED,
                    OPERATION_UPDATE_CHARGING_PROFILE, null, false),
            new CommandChannel(GROUP_CHARGING_PROFILE, CHANNEL_MIN_STATE_OF_CHARGE, OPERATION_UPDATE_CHARGING_PROFILE,
                    null, false),
            new CommandChannel(GROUP_CLIMATE, CHANNEL_AIR_CONDITIONING, OPERATION_START_AIR_CONDITIONING,
                    OPERATION_STOP_AIR_CONDITIONING, true),
            new CommandChannel(GROUP_CLIMATE, CHANNEL_TARGET_TEMPERATURE, OPERATION_START_AIR_CONDITIONING, null,
                    false),
            new CommandChannel(GROUP_CLIMATE, CHANNEL_WITHOUT_EXTERNAL_POWER, OPERATION_START_AIR_CONDITIONING, null,
                    false),
            new CommandChannel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_HEATING, OPERATION_START_AUXILIARY_HEATING,
                    OPERATION_STOP_AUXILIARY_HEATING, true),
            new CommandChannel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_TARGET_TEMPERATURE,
                    OPERATION_START_AUXILIARY_HEATING, null, false),
            new CommandChannel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_DURATION, OPERATION_START_AUXILIARY_HEATING,
                    null, false),
            new CommandChannel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_START_MODE, OPERATION_START_AUXILIARY_HEATING,
                    null, false),
            new CommandChannel(GROUP_ACTIVE_VENTILATION, CHANNEL_ACTIVE_VENTILATION, OPERATION_START_ACTIVE_VENTILATION,
                    OPERATION_STOP_ACTIVE_VENTILATION, true));

    private final Logger logger = LoggerFactory.getLogger(MySkodaVehicleHandler.class);
    private final MySkodaStateDescriptionProvider stateDescriptionProvider;

    private MySkodaVehicleConfiguration config = new MySkodaVehicleConfiguration();
    private @Nullable ScheduledFuture<?> pollingJob;

    private final StartParameter<Double> climateTargetTemperatureCelsius = new StartParameter<>(21.0);
    private final StartParameter<Boolean> airConditioningWithoutExternalPower = new StartParameter<>(true);
    private final StartParameter<Double> auxiliaryHeatingTargetTemperatureCelsius = new StartParameter<>(21.0);
    private final StartParameter<Integer> auxiliaryHeatingDurationSeconds = new StartParameter<>(600);
    private final StartParameter<String> auxiliaryHeatingStartMode = new StartParameter<>("HEATING");

    // serializes charging profile changes, each of which reads and then replaces the whole profile
    private final Object chargingProfileLock = new Object();
    private final PendingProfileChanges pendingProfileChanges = new PendingProfileChanges();

    // operations the vehicle supports; null while unknown
    private volatile @Nullable Set<String> supportedOperations;
    // channel groups whose data the vehicle does not support; only used by the polling job
    private final Set<String> unsupportedGroups = new HashSet<>();

    public MySkodaVehicleHandler(Thing thing, MySkodaStateDescriptionProvider stateDescriptionProvider) {
        super(thing);
        this.stateDescriptionProvider = stateDescriptionProvider;
    }

    @Override
    public void initialize() {
        config = getConfigAs(MySkodaVehicleConfiguration.class);
        if (config.vin.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "@text/myskoda.vehicle.no-vin");
            return;
        }
        if (config.refreshInterval < 1) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/myskoda.vehicle.invalid-refresh-interval");
            return;
        }
        updateStatus(ThingStatus.UNKNOWN);
        pollingJob = scheduler.scheduleWithFixedDelay(this::updateData, 0, config.refreshInterval, TimeUnit.MINUTES);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        super.dispose();
    }

    private @Nullable MySkodaAccountHandler getAccountHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }
        return bridge.getHandler() instanceof MySkodaAccountHandler handler ? handler : null;
    }

    private void updateData() {
        MySkodaAccountHandler accountHandler = getAccountHandler();
        if (accountHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }
        MySkodaApiClient apiClient = accountHandler.getApiClient();
        if (apiClient == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }
        try {
            VehicleResponse response = apiClient.getVehicle(config.vin);
            accountHandler.refreshApiKeyExpiryProperty();
            Vehicle vehicle = response.vehicle;
            if (vehicle == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "@text/myskoda.vehicle.empty-response");
                return;
            }
            updateStatus(ThingStatus.ONLINE);
            updateVehicleProperties(vehicle);
            updateChannelStructure(vehicle, response.errors);
            updateStatusGroup(vehicle.status);
            updateOdometerGroup(vehicle.odometer);
            updateFuelGroup(vehicle.fuelStatus);
            updatePositionGroup(vehicle.parkingPosition);
            updateChargingGroup(vehicle.charging);
            updateClimateGroup(vehicle.airConditioning);
            updateAuxiliaryHeatingGroup(vehicle.auxiliaryHeating);
            updateActiveVentilationGroup(vehicle.activeVentilation);
            updateChargingProfileGroup(vehicle.chargingProfiles);
        } catch (MySkodaAuthException e) {
            accountHandler.reportAuthError(e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        } catch (MySkodaRateLimitException e) {
            logger.debug("MySkoda API rate limit hit while polling {}: {}", config.vin, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (MySkodaApiException e) {
            logger.debug("Error updating MySkoda vehicle {}", config.vin, e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateVehicleProperties(Vehicle vehicle) {
        if (!vehicle.name.isBlank()) {
            updateProperty(PROPERTY_VEHICLE_NAME, vehicle.name);
        }
        if (!vehicle.licensePlate.isBlank()) {
            updateProperty(PROPERTY_LICENSE_PLATE, vehicle.licensePlate);
        }
    }

    /**
     * Track which data groups and operations the vehicle supports and adapt the thing's channels
     * when that changes. Package-private for tests.
     */
    void updateChannelStructure(Vehicle vehicle, List<VehicleError> errors) {
        boolean changed = false;
        for (DataGroup dataGroup : DATA_GROUPS) {
            if (dataGroup.part().apply(vehicle) != null) {
                changed |= unsupportedGroups.remove(dataGroup.group());
            } else if (isUnsupported(dataGroup, errors)) {
                changed |= unsupportedGroups.add(dataGroup.group());
            }
        }
        List<VehicleOperation> operations = vehicle.operations;
        if (operations != null) {
            Set<String> supported = operations.stream().map(operation -> operation.name)
                    .collect(Collectors.toUnmodifiableSet());
            if (!supported.equals(supportedOperations)) {
                supportedOperations = supported;
                changed = true;
            }
        }
        if (changed) {
            applyChannelStructure();
        }
    }

    /**
     * A part missing from the response is unsupported unless an error reports it as currently
     * disabled or unavailable. Without {@code include}, the API omits unsupported parts without an
     * error, but an {@code *_UNSUPPORTED} error is accepted as well.
     */
    private static boolean isUnsupported(DataGroup dataGroup, List<VehicleError> errors) {
        for (VehicleError error : errors) {
            if (error.type.equals(dataGroup.errorPrefix() + "_DISABLED")
                    || error.type.equals(dataGroup.errorPrefix() + "_UNAVAILABLE")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Remove the channel groups of unsupported data and the controls of unsupported operations,
     * and make read-only the settings whose operation is unsupported - or undo all that once the
     * vehicle supports them again.
     */
    private void applyChannelStructure() {
        ThingHandlerCallback callback = getCallback();
        if (callback == null) {
            return;
        }
        Map<ChannelUID, Channel> channels = new LinkedHashMap<>();
        getThing().getChannels().forEach(channel -> channels.put(channel.getUID(), channel));
        Set<ChannelUID> before = Set.copyOf(channels.keySet());

        for (DataGroup dataGroup : DATA_GROUPS) {
            String group = dataGroup.group();
            if (unsupportedGroups.contains(group)) {
                channels.keySet().removeIf(channelUID -> group.equals(channelUID.getGroupId()));
            } else if (channels.keySet().stream().noneMatch(channelUID -> group.equals(channelUID.getGroupId()))) {
                callback.createChannelBuilders(new ChannelGroupUID(getThing().getUID(), group),
                        new ChannelGroupTypeUID(BINDING_ID, dataGroup.groupType())).forEach(builder -> {
                            Channel channel = builder.build();
                            channels.put(channel.getUID(), channel);
                        });
            }
        }

        Set<String> supported = supportedOperations;
        for (CommandChannel commandChannel : COMMAND_CHANNELS) {
            ChannelUID channelUID = channel(commandChannel.group(), commandChannel.channel());
            boolean isSupported = supported == null || commandChannel.isSupportedBy(supported);
            if (!commandChannel.control()) {
                stateDescriptionProvider.setReadOnly(channelUID, !isSupported);
            } else if (!isSupported) {
                channels.remove(channelUID);
            } else if (!unsupportedGroups.contains(commandChannel.group()) && !channels.containsKey(channelUID)) {
                channels.put(channelUID, callback
                        .createChannelBuilder(channelUID, new ChannelTypeUID(BINDING_ID, commandChannel.channel()))
                        .build());
            }
        }

        if (!channels.keySet().equals(before)) {
            logger.debug("Adapting channels of vehicle {}: unsupported groups {}, supported operations {}", config.vin,
                    unsupportedGroups, supported);
            updateThing(editThing().withChannels(new ArrayList<>(channels.values())).build());
        }
    }

    /**
     * @return the operation a command to a channel needs, or null if it needs none
     */
    private static @Nullable String requiredOperation(ChannelUID channelUID, Command command) {
        for (CommandChannel commandChannel : COMMAND_CHANNELS) {
            if (commandChannel.group().equals(channelUID.getGroupId())
                    && commandChannel.channel().equals(channelUID.getIdWithoutGroup())) {
                String stop = commandChannel.stopOperation();
                return stop != null && command == OnOffType.OFF ? stop : commandChannel.operation();
            }
        }
        return null;
    }

    private void updateStatusGroup(@Nullable VehicleStatus status) {
        if (status == null) {
            return;
        }
        OverallVehicleStatus overall = status.overall;
        if (overall != null) {
            updateState(channel(GROUP_STATUS, CHANNEL_OVERALL_DOORS_LOCKED), new StringType(overall.doorsLocked));
            updateState(channel(GROUP_STATUS, CHANNEL_LOCKED), lockedState(overall.locked));
            updateState(channel(GROUP_STATUS, CHANNEL_RELIABLE_LOCK_STATUS), stringOrUndef(overall.reliableLockStatus));
            updateState(channel(GROUP_STATUS, CHANNEL_DOORS), stringOrUndef(overall.doors));
            updateState(channel(GROUP_STATUS, CHANNEL_WINDOWS), stringOrUndef(overall.windows));
            updateState(channel(GROUP_STATUS, CHANNEL_LIGHTS), stringOrUndef(overall.lights));
        }
        VehicleStatusDetail detail = status.detail;
        if (detail != null) {
            updateState(channel(GROUP_STATUS, CHANNEL_SUNROOF), stringOrUndef(detail.sunroof));
            updateState(channel(GROUP_STATUS, CHANNEL_TRUNK), stringOrUndef(detail.trunk));
            updateState(channel(GROUP_STATUS, CHANNEL_BONNET), stringOrUndef(detail.bonnet));
        }
        updateState(channel(GROUP_STATUS, CHANNEL_LAST_UPDATED), dateTimeOrUndef(status.carCapturedTimestamp));
    }

    private State lockedState(String locked) {
        return switch (locked) {
            case "YES" -> OnOffType.ON;
            case "NO" -> OnOffType.OFF;
            default -> UnDefType.UNDEF;
        };
    }

    private void updateOdometerGroup(@Nullable Odometer odometer) {
        if (odometer == null) {
            return;
        }
        updateState(channel(GROUP_ODOMETER, CHANNEL_MILEAGE),
                new QuantityType<>(odometer.mileageInKm, MetricPrefix.KILO(SIUnits.METRE)));
        updateState(channel(GROUP_ODOMETER, CHANNEL_LAST_UPDATED), dateTimeOrUndef(odometer.carCapturedTimestamp));
    }

    private void updateFuelGroup(@Nullable FuelStatus fuelStatus) {
        if (fuelStatus == null) {
            return;
        }
        updateState(channel(GROUP_FUEL, CHANNEL_CAR_TYPE), stringOrUndef(fuelStatus.carType));
        updateState(channel(GROUP_FUEL, CHANNEL_ADBLUE_RANGE), lengthKmOrUndef(fuelStatus.adBlueRange));
        updateState(channel(GROUP_FUEL, CHANNEL_TOTAL_RANGE), lengthKmOrUndef(fuelStatus.totalRangeInKm));
        updateEngineRange(fuelStatus.primaryEngineRange, CHANNEL_PRIMARY_ENGINE_TYPE, CHANNEL_PRIMARY_STATE_OF_CHARGE,
                CHANNEL_PRIMARY_FUEL_LEVEL, CHANNEL_PRIMARY_RANGE);
        updateEngineRange(fuelStatus.secondaryEngineRange, CHANNEL_SECONDARY_ENGINE_TYPE,
                CHANNEL_SECONDARY_STATE_OF_CHARGE, CHANNEL_SECONDARY_FUEL_LEVEL, CHANNEL_SECONDARY_RANGE);
        updateState(channel(GROUP_FUEL, CHANNEL_LAST_UPDATED), dateTimeOrUndef(fuelStatus.carCapturedTimestamp));
    }

    private void updateEngineRange(@Nullable EngineRange range, String typeChannel, String socChannel,
            String fuelLevelChannel, String rangeChannel) {
        if (range == null) {
            return;
        }
        updateState(channel(GROUP_FUEL, typeChannel), stringOrUndef(range.engineType));
        updateState(channel(GROUP_FUEL, socChannel), percentOrUndef(range.currentSoCInPercent));
        updateState(channel(GROUP_FUEL, fuelLevelChannel), percentOrUndef(range.currentFuelLevelInPercent));
        updateState(channel(GROUP_FUEL, rangeChannel), lengthKmOrUndef(range.remainingRangeInKm));
    }

    private void updatePositionGroup(@Nullable ParkingPosition position) {
        if (position == null) {
            return;
        }
        updateState(channel(GROUP_POSITION, CHANNEL_PARKING_STATE), stringOrUndef(position.state));
        GpsCoordinates coordinates = position.gpsCoordinates;
        if (coordinates != null) {
            updateState(channel(GROUP_POSITION, CHANNEL_LOCATION),
                    new PointType(new DecimalType(coordinates.latitude), new DecimalType(coordinates.longitude)));
        }
        updateState(channel(GROUP_POSITION, CHANNEL_ADDRESS), stringOrUndef(position.formattedAddress));
    }

    private void updateChargingGroup(@Nullable Charging charging) {
        if (charging == null) {
            return;
        }
        ChargingStatus status = charging.status;
        if (status != null) {
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGING_STATE), stringOrUndef(status.state));
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGE_TYPE), stringOrUndef(status.chargeType));
            updateState(channel(GROUP_CHARGING, CHANNEL_PLUG_CONNECTION_STATE),
                    stringOrUndef(status.plugConnectionState));
            updateState(channel(GROUP_CHARGING, CHANNEL_PLUG_LOCK_STATE), stringOrUndef(status.plugLockState));
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGE_POWER), powerOrUndef(status.chargePowerInKw));
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGE_RATE),
                    speedKmhOrUndef(status.chargingRateInKilometersPerHour));
            updateState(channel(GROUP_CHARGING, CHANNEL_REMAINING_TIME),
                    minutesOrUndef(status.remainingTimeToFullyChargedInMinutes));
            updateState(channel(GROUP_CHARGING, CHANNEL_FULLY_CHARGED_AT), dateTimeOrUndef(status.fullyChargedAt));
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGING), OnOffType.from("CHARGING".equals(status.state)));
            if (status.battery != null) {
                updateState(channel(GROUP_CHARGING, CHANNEL_STATE_OF_CHARGE),
                        percentOrUndef(status.battery.stateOfChargeInPercent));
                Integer rangeInMeters = status.battery.remainingCruisingRangeInMeters;
                State remainingRangeState = UnDefType.UNDEF;
                if (rangeInMeters != null) {
                    QuantityType<?> inKm = new QuantityType<>(rangeInMeters, SIUnits.METRE)
                            .toUnit(MetricPrefix.KILO(SIUnits.METRE));
                    if (inKm != null) {
                        remainingRangeState = inKm;
                    }
                }
                updateState(channel(GROUP_CHARGING, CHANNEL_REMAINING_RANGE), remainingRangeState);
            }
        }
        ChargingSettings settings = charging.settings;
        if (settings != null) {
            updateState(channel(GROUP_CHARGING, CHANNEL_TARGET_STATE_OF_CHARGE),
                    percentOrUndef(settings.targetStateOfChargeInPercent));
            updateState(channel(GROUP_CHARGING, CHANNEL_BATTERY_CARE_MODE_TARGET),
                    percentOrUndef(settings.batteryCareModeTargetValueInPercent));
            updateState(channel(GROUP_CHARGING, CHANNEL_PREFERRED_CHARGE_MODE),
                    stringOrUndef(settings.preferredChargeMode));
            List<String> availableChargeModes = settings.availableChargeModes;
            if (availableChargeModes != null && !availableChargeModes.isEmpty()) {
                stateDescriptionProvider.setStateOptions(channel(GROUP_CHARGING, CHANNEL_PREFERRED_CHARGE_MODE),
                        availableChargeModes.stream().map(mode -> new StateOption(mode, optionLabel(mode))).toList());
            }
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGING_CARE_MODE), stringOrUndef(settings.chargingCareMode));
            updateState(channel(GROUP_CHARGING, CHANNEL_AUTO_UNLOCK_PLUG),
                    stringOrUndef(settings.autoUnlockPlugWhenCharged));
            updateState(channel(GROUP_CHARGING, CHANNEL_MAX_CHARGE_CURRENT),
                    stringOrUndef(settings.maxChargeCurrentAc));
            Integer maxChargeCurrentAcAmpere = settings.maxChargeCurrentAcAmpere;
            updateState(channel(GROUP_CHARGING, CHANNEL_MAX_CHARGE_CURRENT_AMPERE),
                    maxChargeCurrentAcAmpere == null ? UnDefType.UNDEF : new DecimalType(maxChargeCurrentAcAmpere));
        }
        updateState(channel(GROUP_CHARGING, CHANNEL_LAST_UPDATED), dateTimeOrUndef(charging.carCapturedTimestamp));
    }

    private void updateClimateGroup(@Nullable AirConditioning airConditioning) {
        if (airConditioning == null) {
            return;
        }
        updateState(channel(GROUP_CLIMATE, CHANNEL_CLIMATE_STATE), stringOrUndef(airConditioning.state));
        TargetTemperature targetTemperature = airConditioning.targetTemperature;
        if (targetTemperature != null) {
            updateState(channel(GROUP_CLIMATE, CHANNEL_TARGET_TEMPERATURE),
                    new QuantityType<>(
                            climateTargetTemperatureCelsius.updateFromVehicle(celsiusValue(targetTemperature)),
                            SIUnits.CELSIUS));
        }
        updateState(channel(GROUP_CLIMATE, CHANNEL_ESTIMATED_REACH_TARGET_TEMPERATURE_AT),
                dateTimeOrUndef(airConditioning.estimatedReachOfTargetTemperatureAt));
        Boolean withoutExternalPower = airConditioning.airConditioningWithoutExternalPower;
        if (withoutExternalPower != null) {
            updateState(channel(GROUP_CLIMATE, CHANNEL_WITHOUT_EXTERNAL_POWER),
                    OnOffType.from(airConditioningWithoutExternalPower.updateFromVehicle(withoutExternalPower)));
        }
        updateState(channel(GROUP_CLIMATE, CHANNEL_AT_UNLOCK), booleanOrUndef(airConditioning.airConditioningAtUnlock));
        if (airConditioning.windowHeating != null) {
            updateState(channel(GROUP_CLIMATE, CHANNEL_WINDOW_HEATING_ENABLED),
                    booleanOrUndef(airConditioning.windowHeating.enabled));
            updateState(channel(GROUP_CLIMATE, CHANNEL_WINDOW_HEATING_FRONT),
                    stringOrUndef(airConditioning.windowHeating.front));
            updateState(channel(GROUP_CLIMATE, CHANNEL_WINDOW_HEATING_REAR),
                    stringOrUndef(airConditioning.windowHeating.rear));
        }
        boolean active = switch (airConditioning.state) {
            case "COOLING", "HEATING", "HEATING_AUXILIARY", "VENTILATION" -> true;
            default -> false;
        };
        updateState(channel(GROUP_CLIMATE, CHANNEL_AIR_CONDITIONING), OnOffType.from(active));
        updateState(channel(GROUP_CLIMATE, CHANNEL_LAST_UPDATED),
                dateTimeOrUndef(airConditioning.carCapturedTimestamp));
    }

    private void updateAuxiliaryHeatingGroup(@Nullable AuxiliaryHeating auxiliaryHeating) {
        if (auxiliaryHeating == null) {
            return;
        }
        updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_HEATING_STATE),
                stringOrUndef(auxiliaryHeating.state));
        if (!auxiliaryHeating.startMode.isBlank()) {
            updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_START_MODE),
                    new StringType(auxiliaryHeatingStartMode.updateFromVehicle(auxiliaryHeating.startMode)));
        }
        Integer durationInSeconds = auxiliaryHeating.durationInSeconds;
        if (durationInSeconds != null) {
            updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_DURATION), new QuantityType<>(
                    auxiliaryHeatingDurationSeconds.updateFromVehicle(durationInSeconds), Units.SECOND));
        }
        TargetTemperature targetTemperature = auxiliaryHeating.targetTemperature;
        if (targetTemperature != null) {
            updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_TARGET_TEMPERATURE),
                    new QuantityType<>(
                            auxiliaryHeatingTargetTemperatureCelsius.updateFromVehicle(celsiusValue(targetTemperature)),
                            SIUnits.CELSIUS));
        }
        updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_ESTIMATED_REACH_TARGET_AT),
                dateTimeOrUndef(auxiliaryHeating.estimatedReachOfTargetTemperatureAt));
        boolean active = switch (auxiliaryHeating.state) {
            case "PREHEATING", "HEATING_AUXILIARY", "VENTILATION" -> true;
            default -> false;
        };
        updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_HEATING), OnOffType.from(active));
    }

    private void updateActiveVentilationGroup(@Nullable ActiveVentilation activeVentilation) {
        if (activeVentilation == null) {
            return;
        }
        updateState(channel(GROUP_ACTIVE_VENTILATION, CHANNEL_ACTIVE_VENTILATION_STATE),
                stringOrUndef(activeVentilation.state));
        Integer ventilationDuration = activeVentilation.durationInSeconds;
        if (ventilationDuration != null) {
            updateState(channel(GROUP_ACTIVE_VENTILATION, CHANNEL_ACTIVE_VENTILATION_DURATION),
                    new QuantityType<>(ventilationDuration, Units.SECOND));
        }
        boolean active = switch (activeVentilation.state) {
            case "PREHEATING", "VENTILATION" -> true;
            default -> false;
        };
        updateState(channel(GROUP_ACTIVE_VENTILATION, CHANNEL_ACTIVE_VENTILATION), OnOffType.from(active));
    }

    private void updateChargingProfileGroup(@Nullable ChargingProfiles profiles) {
        if (profiles == null) {
            return;
        }
        JsonObject raw = selectChargingProfile(profiles);
        ChargingProfile profile = raw == null ? null : ChargingProfileSupport.parse(raw);
        CurrentVehiclePositionProfile current = profiles.currentVehiclePositionProfile;
        boolean atLocation = profile != null && current != null && current.id == profile.id;
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_AT_PROFILE_LOCATION), OnOffType.from(atLocation));
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_NEXT_CHARGING_TIME),
                atLocation ? stringOrUndef(current.nextChargingTime) : UnDefType.UNDEF);
        updateChargingProfileChannels(profile);
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_LAST_UPDATED),
                dateTimeOrUndef(profiles.carCapturedTimestamp));
    }

    /**
     * @return the charging profile to show, with the changes still pending applied
     */
    private @Nullable JsonObject selectChargingProfile(ChargingProfiles profiles) {
        JsonObject raw = ChargingProfileSupport.select(profiles, config.chargingProfile);
        ChargingProfile parsed = raw == null ? null : ChargingProfileSupport.parse(raw);
        return raw == null || parsed == null ? null : pendingProfileChanges.applyTo(parsed.id, raw);
    }

    private void updateChargingProfileChannels(@Nullable ChargingProfile profile) {
        ChargingProfileSettings settings = profile == null ? null : profile.settings;
        MinBatteryStateOfCharge minStateOfCharge = settings == null ? null : settings.minBatteryStateOfCharge;
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_PROFILE_NAME),
                profile == null ? UnDefType.UNDEF : stringOrUndef(profile.name));
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_TARGET_STATE_OF_CHARGE),
                settings == null ? UnDefType.UNDEF : percentOrUndef(settings.targetStateOfChargeInPercent));
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_MAX_CHARGE_CURRENT),
                settings == null ? UnDefType.UNDEF : stringOrUndef(settings.maxChargingCurrent));
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_AUTO_UNLOCK_PLUG),
                settings == null ? UnDefType.UNDEF : stringOrUndef(settings.autoUnlockPlugWhenCharged));
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_MIN_STATE_OF_CHARGE_ENABLED),
                minStateOfCharge == null ? UnDefType.UNDEF : booleanOrUndef(minStateOfCharge.enabled));
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_MIN_STATE_OF_CHARGE),
                minStateOfCharge == null ? UnDefType.UNDEF
                        : percentOrUndef(minStateOfCharge.minimumBatteryStateOfChargeInPercent));
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_TIMERS), profile == null ? UnDefType.UNDEF
                : new StringType(ChargingProfileSupport.formatTimers(profile.timers)));
        updateState(channel(GROUP_CHARGING_PROFILE, CHANNEL_PREFERRED_CHARGING_TIMES), profile == null ? UnDefType.UNDEF
                : new StringType(ChargingProfileSupport.formatChargingTimes(profile.preferredChargingTimes)));
    }

    /**
     * Change one setting of the shown charging profile. The API replaces a profile as a whole, so
     * the profile is read again right before the change - it may have been edited in the MyŠkoda
     * app since the last poll - and sent back complete with only that setting changed. Changes
     * still pending from earlier commands are applied on top, see {@link PendingProfileChanges}.
     */
    private void handleChargingProfileCommand(MySkodaApiClient apiClient, ChannelUID channelUID, Command command)
            throws MySkodaApiException, InterruptedException {
        JsonElement value;
        String[] path;
        switch (channelUID.getIdWithoutGroup()) {
            case CHANNEL_TARGET_STATE_OF_CHARGE:
                Integer targetStateOfCharge = percentValue(command);
                if (targetStateOfCharge == null) {
                    return;
                }
                value = new JsonPrimitive(targetStateOfCharge);
                path = new String[] { "targetStateOfChargeInPercent" };
                break;
            case CHANNEL_MAX_CHARGE_CURRENT:
                if (!(command instanceof StringType)) {
                    return;
                }
                value = new JsonPrimitive(command.toString());
                path = new String[] { "maxChargingCurrent" };
                break;
            case CHANNEL_AUTO_UNLOCK_PLUG:
                if (!(command instanceof StringType)) {
                    return;
                }
                value = new JsonPrimitive(command.toString());
                path = new String[] { "autoUnlockPlugWhenCharged" };
                break;
            case CHANNEL_MIN_STATE_OF_CHARGE_ENABLED:
                if (!(command instanceof OnOffType onOff)) {
                    return;
                }
                value = new JsonPrimitive(onOff == OnOffType.ON);
                path = new String[] { "minBatteryStateOfCharge", "enabled" };
                break;
            case CHANNEL_MIN_STATE_OF_CHARGE:
                Integer minStateOfCharge = percentValue(command);
                if (minStateOfCharge == null) {
                    return;
                }
                value = new JsonPrimitive(minStateOfCharge);
                path = new String[] { "minBatteryStateOfCharge", "minimumBatteryStateOfChargeInPercent" };
                break;
            default:
                logger.debug("Channel {} does not accept commands", channelUID);
                return;
        }
        synchronized (chargingProfileLock) {
            Vehicle vehicle = apiClient.getVehicle(config.vin, "chargingProfiles").vehicle;
            ChargingProfiles profiles = vehicle == null ? null : vehicle.chargingProfiles;
            if (profiles == null) {
                logger.warn("Cannot change charging profile of vehicle {}: the vehicle did not report its profiles",
                        config.vin);
                return;
            }
            JsonObject profile = selectChargingProfile(profiles);
            ChargingProfile parsed = profile == null ? null : ChargingProfileSupport.parse(profile);
            if (profile == null || parsed == null) {
                logger.warn("Cannot change charging profile of vehicle {}: {}", config.vin,
                        config.chargingProfile.isBlank() ? "the vehicle is not at a saved charging location"
                                : "no charging profile '" + config.chargingProfile + "' found");
                updateChargingProfileGroup(profiles);
                return;
            }
            apiClient.updateChargingProfile(config.vin, parsed.id,
                    ChargingProfileSupport.withSetting(profile, value, path));
            pendingProfileChanges.add(parsed.id, value, path);
            updateChargingProfileGroup(profiles);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // commands consume the same quota as polling, so a REFRESH does not trigger an
            // out-of-band API call - the next scheduled poll will update the channel
            return;
        }
        String operation = requiredOperation(channelUID, command);
        Set<String> supported = supportedOperations;
        if (operation != null && supported != null && !supported.contains(operation)) {
            logger.warn("Vehicle {} does not support {}, ignoring command {} to {}", config.vin, operation, command,
                    channelUID);
            return;
        }
        MySkodaAccountHandler accountHandler = getAccountHandler();
        if (accountHandler == null) {
            logger.debug("Cannot handle command {} for {}, bridge is not initialized", command, channelUID);
            return;
        }
        MySkodaApiClient apiClient = accountHandler.getApiClient();
        if (apiClient == null) {
            logger.debug("Cannot handle command {} for {}, bridge is not initialized", command, channelUID);
            return;
        }
        try {
            if (GROUP_CHARGING_PROFILE.equals(channelUID.getGroupId())) {
                handleChargingProfileCommand(apiClient, channelUID, command);
                return;
            }
            switch (channelUID.getIdWithoutGroup()) {
                case CHANNEL_CHARGING:
                    if (command == OnOffType.ON) {
                        apiClient.startCharging(config.vin);
                    } else if (command == OnOffType.OFF) {
                        apiClient.stopCharging(config.vin);
                    }
                    break;
                case CHANNEL_TARGET_STATE_OF_CHARGE:
                    Integer targetStateOfCharge = percentValue(command);
                    if (targetStateOfCharge != null) {
                        apiClient.setChargingLimit(config.vin, targetStateOfCharge);
                    }
                    break;
                case CHANNEL_PREFERRED_CHARGE_MODE:
                    if (command instanceof StringType) {
                        apiClient.setChargeMode(config.vin, command.toString());
                    }
                    break;
                case CHANNEL_TARGET_TEMPERATURE:
                    climateTargetTemperatureCelsius.set(celsiusValue(command, climateTargetTemperatureCelsius.get()));
                    updateState(channelUID, new QuantityType<>(climateTargetTemperatureCelsius.get(), SIUnits.CELSIUS));
                    break;
                case CHANNEL_WITHOUT_EXTERNAL_POWER:
                    if (command instanceof OnOffType onOff) {
                        airConditioningWithoutExternalPower.set(onOff == OnOffType.ON);
                    }
                    break;
                case CHANNEL_AIR_CONDITIONING:
                    if (command == OnOffType.ON) {
                        apiClient.startAirConditioning(config.vin,
                                new TargetTemperature(climateTargetTemperatureCelsius.get(), "CELSIUS"),
                                airConditioningWithoutExternalPower.get());
                        climateTargetTemperatureCelsius.sent();
                        airConditioningWithoutExternalPower.sent();
                    } else if (command == OnOffType.OFF) {
                        apiClient.stopAirConditioning(config.vin);
                    }
                    break;
                case CHANNEL_AUXILIARY_TARGET_TEMPERATURE:
                    auxiliaryHeatingTargetTemperatureCelsius
                            .set(celsiusValue(command, auxiliaryHeatingTargetTemperatureCelsius.get()));
                    updateState(channelUID,
                            new QuantityType<>(auxiliaryHeatingTargetTemperatureCelsius.get(), SIUnits.CELSIUS));
                    break;
                case CHANNEL_AUXILIARY_DURATION:
                    if (command instanceof QuantityType<?> quantity) {
                        QuantityType<?> seconds = quantity.toUnit(Units.SECOND);
                        if (seconds != null) {
                            auxiliaryHeatingDurationSeconds.set(seconds.intValue());
                        }
                    } else if (command instanceof DecimalType decimal) {
                        auxiliaryHeatingDurationSeconds.set(decimal.intValue());
                    }
                    updateState(channelUID, new QuantityType<>(auxiliaryHeatingDurationSeconds.get(), Units.SECOND));
                    break;
                case CHANNEL_AUXILIARY_START_MODE:
                    if (command instanceof StringType && AUXILIARY_START_MODES.contains(command.toString())) {
                        auxiliaryHeatingStartMode.set(command.toString());
                    } else {
                        logger.warn("Invalid auxiliary heating start mode '{}', expected one of {}", command,
                                AUXILIARY_START_MODES);
                        updateState(channelUID, new StringType(auxiliaryHeatingStartMode.get()));
                    }
                    break;
                case CHANNEL_AUXILIARY_HEATING:
                    if (command == OnOffType.ON) {
                        if (config.sPin.isBlank()) {
                            logger.warn(
                                    "Cannot start auxiliary heating for {}: no S-PIN configured on the vehicle thing",
                                    config.vin);
                            updateState(channelUID, OnOffType.OFF);
                            break;
                        }
                        apiClient.startAuxiliaryHeating(config.vin, config.sPin, auxiliaryHeatingDurationSeconds.get(),
                                auxiliaryHeatingStartMode.get(),
                                new TargetTemperature(auxiliaryHeatingTargetTemperatureCelsius.get(), "CELSIUS"));
                        auxiliaryHeatingDurationSeconds.sent();
                        auxiliaryHeatingStartMode.sent();
                        auxiliaryHeatingTargetTemperatureCelsius.sent();
                    } else if (command == OnOffType.OFF) {
                        apiClient.stopAuxiliaryHeating(config.vin);
                    }
                    break;
                case CHANNEL_ACTIVE_VENTILATION:
                    if (command == OnOffType.ON) {
                        apiClient.startActiveVentilation(config.vin);
                    } else if (command == OnOffType.OFF) {
                        apiClient.stopActiveVentilation(config.vin);
                    }
                    break;
                default:
                    logger.debug("Channel {} does not accept commands", channelUID);
                    break;
            }
        } catch (MySkodaAuthException e) {
            accountHandler.reportAuthError(e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        } catch (MySkodaRateLimitException e) {
            logger.warn("Cannot send command {} to {} for vehicle {}: {}", command, channelUID, config.vin,
                    e.getMessage());
        } catch (MySkodaApiException e) {
            logger.warn("Error sending command {} to {} for vehicle {}: {}", command, channelUID, config.vin,
                    e.getMessage());
            logger.debug("Command failure details", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ChannelUID channel(String group, String id) {
        return new ChannelUID(getThing().getUID(), group, id);
    }

    private static double celsiusValue(TargetTemperature targetTemperature) {
        if ("FAHRENHEIT".equals(targetTemperature.unit)) {
            return new QuantityType<>(targetTemperature.value, ImperialUnits.FAHRENHEIT).toUnit(SIUnits.CELSIUS)
                    .doubleValue();
        }
        return targetTemperature.value;
    }

    private static double celsiusValue(Command command, double fallback) {
        if (command instanceof QuantityType<?> quantity) {
            QuantityType<?> celsius = quantity.toUnit(SIUnits.CELSIUS);
            return celsius == null ? fallback : celsius.doubleValue();
        } else if (command instanceof DecimalType decimal) {
            return decimal.doubleValue();
        }
        return fallback;
    }

    private static @Nullable Integer percentValue(Command command) {
        if (command instanceof QuantityType<?> quantity) {
            QuantityType<?> percent = quantity.toUnit(Units.PERCENT);
            return percent == null ? null : (int) Math.round(percent.doubleValue());
        } else if (command instanceof DecimalType decimal) {
            return (int) Math.round(decimal.doubleValue());
        }
        return null;
    }

    /**
     * Turn an API enum value such as {@code TIMER_CHARGING_WITH_CLIMATISATION} into a label
     * ("Timer charging with climatisation"), matching the labels of the static channel options.
     */
    private static String optionLabel(String value) {
        String words = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static State stringOrUndef(String value) {
        return value.isBlank() ? UnDefType.UNDEF : new StringType(value);
    }

    private static State booleanOrUndef(@Nullable Boolean value) {
        return value == null ? UnDefType.UNDEF : OnOffType.from(value);
    }

    private static State percentOrUndef(@Nullable Number value) {
        return value == null ? UnDefType.UNDEF : new QuantityType<>(value, Units.PERCENT);
    }

    private static State lengthKmOrUndef(@Nullable Double value) {
        return value == null ? UnDefType.UNDEF : new QuantityType<>(value, MetricPrefix.KILO(SIUnits.METRE));
    }

    private static State powerOrUndef(@Nullable Double value) {
        return value == null ? UnDefType.UNDEF : new QuantityType<>(value, MetricPrefix.KILO(Units.WATT));
    }

    private static State speedKmhOrUndef(@Nullable Double value) {
        return value == null ? UnDefType.UNDEF : new QuantityType<>(value, SIUnits.KILOMETRE_PER_HOUR);
    }

    private static State minutesOrUndef(@Nullable Integer value) {
        return value == null ? UnDefType.UNDEF : new QuantityType<>(value, Units.MINUTE);
    }

    private static State dateTimeOrUndef(String isoTimestamp) {
        Instant instant = parseInstant(isoTimestamp);
        return instant == null ? UnDefType.UNDEF : new DateTimeType(instant);
    }

    private static @Nullable Instant parseInstant(String isoTimestamp) {
        if (isoTimestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(isoTimestamp);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(isoTimestamp).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
