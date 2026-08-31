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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.myskoda.internal.api.MySkodaApiClient;
import org.openhab.binding.myskoda.internal.api.dto.ActiveVentilation;
import org.openhab.binding.myskoda.internal.api.dto.AirConditioning;
import org.openhab.binding.myskoda.internal.api.dto.AuxiliaryHeating;
import org.openhab.binding.myskoda.internal.api.dto.Charging;
import org.openhab.binding.myskoda.internal.api.dto.ChargingSettings;
import org.openhab.binding.myskoda.internal.api.dto.ChargingStatus;
import org.openhab.binding.myskoda.internal.api.dto.EngineRange;
import org.openhab.binding.myskoda.internal.api.dto.FuelStatus;
import org.openhab.binding.myskoda.internal.api.dto.GpsCoordinates;
import org.openhab.binding.myskoda.internal.api.dto.Odometer;
import org.openhab.binding.myskoda.internal.api.dto.OverallVehicleStatus;
import org.openhab.binding.myskoda.internal.api.dto.ParkingPosition;
import org.openhab.binding.myskoda.internal.api.dto.TargetTemperature;
import org.openhab.binding.myskoda.internal.api.dto.Vehicle;
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
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MySkodaVehicleHandler} polls one vehicle's state from the MySkoda public API and
 * dispatches the small set of commands the API supports (charging, air conditioning, auxiliary
 * heating, active ventilation start/stop). Since the account bridge's quota is only 20
 * requests/hour, a command does <b>not</b> trigger an immediate re-poll - the new state is picked
 * up on the next scheduled poll instead.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaVehicleHandler extends BaseThingHandler {

    private static final String DEFAULT_START_MODE = "HEATING";

    private final Logger logger = LoggerFactory.getLogger(MySkodaVehicleHandler.class);

    private MySkodaVehicleConfiguration config = new MySkodaVehicleConfiguration();
    private @Nullable ScheduledFuture<?> pollingJob;

    private double climateTargetTemperatureCelsius = 21.0;
    private double auxiliaryHeatingTargetTemperatureCelsius = 21.0;
    private int auxiliaryHeatingDurationSeconds = 600;

    public MySkodaVehicleHandler(Thing thing) {
        super(thing);
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
            updateStatusGroup(vehicle.status);
            updateOdometerGroup(vehicle.odometer);
            updateFuelGroup(vehicle.fuelStatus);
            updatePositionGroup(vehicle.parkingPosition);
            updateChargingGroup(vehicle.charging);
            updateClimateGroup(vehicle.airConditioning);
            updateAuxiliaryHeatingGroup(vehicle.auxiliaryHeating);
            updateActiveVentilationGroup(vehicle.activeVentilation);
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
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGE_POWER), powerOrUndef(status.chargePowerInKw));
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGE_RATE),
                    speedKmhOrUndef(status.chargingRateInKilometersPerHour));
            updateState(channel(GROUP_CHARGING, CHANNEL_REMAINING_TIME),
                    minutesOrUndef(status.remainingTimeToFullyChargedInMinutes));
            updateState(channel(GROUP_CHARGING, CHANNEL_FULLY_CHARGED_AT), dateTimeOrUndef(status.fullyChargedAt));
            updateState(channel(GROUP_CHARGING, CHANNEL_CHARGING), OnOffType.from("CHARGING".equals(status.state)));
            if (status.battery != null) {
                updateState(channel(GROUP_CHARGING, CHANNEL_STATE_OF_CHARGE),
                        percentOrUndef(status.battery.stateOfChargeInPercent == null ? null
                                : status.battery.stateOfChargeInPercent.doubleValue()));
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
                    percentOrUndef(settings.targetStateOfChargeInPercent == null ? null
                            : settings.targetStateOfChargeInPercent.doubleValue()));
            updateState(channel(GROUP_CHARGING, CHANNEL_BATTERY_CARE_MODE_TARGET),
                    percentOrUndef(settings.batteryCareModeTargetValueInPercent == null ? null
                            : settings.batteryCareModeTargetValueInPercent.doubleValue()));
            updateState(channel(GROUP_CHARGING, CHANNEL_PREFERRED_CHARGE_MODE),
                    stringOrUndef(settings.preferredChargeMode));
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
            climateTargetTemperatureCelsius = celsiusValue(targetTemperature);
            updateState(channel(GROUP_CLIMATE, CHANNEL_TARGET_TEMPERATURE),
                    new QuantityType<>(climateTargetTemperatureCelsius, SIUnits.CELSIUS));
        }
        updateState(channel(GROUP_CLIMATE, CHANNEL_ESTIMATED_REACH_TARGET_TEMPERATURE_AT),
                dateTimeOrUndef(airConditioning.estimatedReachOfTargetTemperatureAt));
        updateState(channel(GROUP_CLIMATE, CHANNEL_WITHOUT_EXTERNAL_POWER),
                booleanOrUndef(airConditioning.airConditioningWithoutExternalPower));
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
        updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_START_MODE),
                stringOrUndef(auxiliaryHeating.startMode));
        if (auxiliaryHeating.durationInSeconds != null) {
            auxiliaryHeatingDurationSeconds = auxiliaryHeating.durationInSeconds;
            updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_DURATION),
                    new QuantityType<>(auxiliaryHeatingDurationSeconds, Units.SECOND));
        }
        TargetTemperature targetTemperature = auxiliaryHeating.targetTemperature;
        if (targetTemperature != null) {
            auxiliaryHeatingTargetTemperatureCelsius = celsiusValue(targetTemperature);
            updateState(channel(GROUP_AUXILIARY_HEATING, CHANNEL_AUXILIARY_TARGET_TEMPERATURE),
                    new QuantityType<>(auxiliaryHeatingTargetTemperatureCelsius, SIUnits.CELSIUS));
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

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // commands consume the same quota as polling, so a REFRESH does not trigger an
            // out-of-band API call - the next scheduled poll will update the channel
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
            switch (channelUID.getIdWithoutGroup()) {
                case CHANNEL_CHARGING:
                    if (command == OnOffType.ON) {
                        apiClient.startCharging(config.vin);
                    } else if (command == OnOffType.OFF) {
                        apiClient.stopCharging(config.vin);
                    }
                    break;
                case CHANNEL_TARGET_TEMPERATURE:
                    climateTargetTemperatureCelsius = celsiusValue(command, climateTargetTemperatureCelsius);
                    updateState(channelUID, new QuantityType<>(climateTargetTemperatureCelsius, SIUnits.CELSIUS));
                    break;
                case CHANNEL_AIR_CONDITIONING:
                    if (command == OnOffType.ON) {
                        apiClient.startAirConditioning(config.vin,
                                new TargetTemperature(climateTargetTemperatureCelsius, "CELSIUS"), true);
                    } else if (command == OnOffType.OFF) {
                        apiClient.stopAirConditioning(config.vin);
                    }
                    break;
                case CHANNEL_AUXILIARY_TARGET_TEMPERATURE:
                    auxiliaryHeatingTargetTemperatureCelsius = celsiusValue(command,
                            auxiliaryHeatingTargetTemperatureCelsius);
                    updateState(channelUID,
                            new QuantityType<>(auxiliaryHeatingTargetTemperatureCelsius, SIUnits.CELSIUS));
                    break;
                case CHANNEL_AUXILIARY_DURATION:
                    if (command instanceof QuantityType<?> quantity) {
                        QuantityType<?> seconds = quantity.toUnit(Units.SECOND);
                        if (seconds != null) {
                            auxiliaryHeatingDurationSeconds = seconds.intValue();
                        }
                    } else if (command instanceof DecimalType decimal) {
                        auxiliaryHeatingDurationSeconds = decimal.intValue();
                    }
                    updateState(channelUID, new QuantityType<>(auxiliaryHeatingDurationSeconds, Units.SECOND));
                    break;
                case CHANNEL_AUXILIARY_HEATING:
                    if (command == OnOffType.ON) {
                        if (config.sPin.isBlank()) {
                            logger.warn(
                                    "Cannot start auxiliary heating for {}: no S-PIN configured on the vehicle thing",
                                    config.vin);
                            break;
                        }
                        apiClient.startAuxiliaryHeating(config.vin, config.sPin, auxiliaryHeatingDurationSeconds,
                                DEFAULT_START_MODE,
                                new TargetTemperature(auxiliaryHeatingTargetTemperatureCelsius, "CELSIUS"));
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
        } catch (MySkodaApiException e) {
            logger.warn("Error sending command {} to {} for vehicle {}", command, channelUID, config.vin, e);
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

    private static State stringOrUndef(String value) {
        return value.isBlank() ? UnDefType.UNDEF : new StringType(value);
    }

    private static State booleanOrUndef(@Nullable Boolean value) {
        return value == null ? UnDefType.UNDEF : OnOffType.from(value);
    }

    private static State percentOrUndef(@Nullable Double value) {
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
