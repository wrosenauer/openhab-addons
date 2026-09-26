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
package org.openhab.binding.myskoda.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link MySkodaBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class MySkodaBindingConstants {

    public static final String BINDING_ID = "myskoda";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_VEHICLE = new ThingTypeUID(BINDING_ID, "vehicle");

    // Bridge properties
    public static final String PROPERTY_API_KEY_EXPIRES_AT = "apiKeyExpiresAt";

    // Vehicle properties
    public static final String PROPERTY_VEHICLE_NAME = "vehicleName";
    public static final String PROPERTY_LICENSE_PLATE = "licensePlate";

    // Channel group ids
    public static final String GROUP_STATUS = "status";
    public static final String GROUP_ODOMETER = "odometer";
    public static final String GROUP_FUEL = "fuel";
    public static final String GROUP_POSITION = "position";
    public static final String GROUP_CHARGING = "charging";
    public static final String GROUP_CLIMATE = "climate";
    public static final String GROUP_AUXILIARY_HEATING = "auxiliaryHeating";
    public static final String GROUP_ACTIVE_VENTILATION = "activeVentilation";
    public static final String GROUP_CHARGING_PROFILE = "chargingProfile";

    // status channel ids
    public static final String CHANNEL_OVERALL_DOORS_LOCKED = "overall-doors-locked";
    public static final String CHANNEL_LOCKED = "locked";
    public static final String CHANNEL_RELIABLE_LOCK_STATUS = "reliable-lock-status";
    public static final String CHANNEL_DOORS = "doors";
    public static final String CHANNEL_WINDOWS = "windows";
    public static final String CHANNEL_LIGHTS = "lights";
    public static final String CHANNEL_SUNROOF = "sunroof";
    public static final String CHANNEL_TRUNK = "trunk";
    public static final String CHANNEL_BONNET = "bonnet";

    // odometer channel ids
    public static final String CHANNEL_MILEAGE = "mileage";

    // fuel channel ids
    public static final String CHANNEL_CAR_TYPE = "car-type";
    public static final String CHANNEL_ADBLUE_RANGE = "adblue-range";
    public static final String CHANNEL_TOTAL_RANGE = "total-range";
    public static final String CHANNEL_PRIMARY_ENGINE_TYPE = "primary-engine-type";
    public static final String CHANNEL_PRIMARY_STATE_OF_CHARGE = "primary-state-of-charge";
    public static final String CHANNEL_PRIMARY_FUEL_LEVEL = "primary-fuel-level";
    public static final String CHANNEL_PRIMARY_RANGE = "primary-range";
    public static final String CHANNEL_SECONDARY_ENGINE_TYPE = "secondary-engine-type";
    public static final String CHANNEL_SECONDARY_STATE_OF_CHARGE = "secondary-state-of-charge";
    public static final String CHANNEL_SECONDARY_FUEL_LEVEL = "secondary-fuel-level";
    public static final String CHANNEL_SECONDARY_RANGE = "secondary-range";

    // position channel ids
    public static final String CHANNEL_PARKING_STATE = "parking-state";
    public static final String CHANNEL_LOCATION = "location";
    public static final String CHANNEL_ADDRESS = "address";

    // charging channel ids
    public static final String CHANNEL_STATE_OF_CHARGE = "state-of-charge";
    public static final String CHANNEL_REMAINING_RANGE = "remaining-range";
    public static final String CHANNEL_CHARGING_STATE = "charging-state";
    public static final String CHANNEL_CHARGE_TYPE = "charge-type";
    public static final String CHANNEL_PLUG_CONNECTION_STATE = "plug-connection-state";
    public static final String CHANNEL_PLUG_LOCK_STATE = "plug-lock-state";
    public static final String CHANNEL_CHARGE_POWER = "charge-power";
    public static final String CHANNEL_CHARGE_RATE = "charge-rate";
    public static final String CHANNEL_REMAINING_TIME = "remaining-time";
    public static final String CHANNEL_FULLY_CHARGED_AT = "fully-charged-at";
    public static final String CHANNEL_TARGET_STATE_OF_CHARGE = "target-state-of-charge";
    public static final String CHANNEL_BATTERY_CARE_MODE_TARGET = "battery-care-mode-target";
    public static final String CHANNEL_PREFERRED_CHARGE_MODE = "preferred-charge-mode";
    public static final String CHANNEL_CHARGING_CARE_MODE = "charging-care-mode";
    public static final String CHANNEL_AUTO_UNLOCK_PLUG = "auto-unlock-plug";
    public static final String CHANNEL_MAX_CHARGE_CURRENT = "max-charge-current";
    public static final String CHANNEL_MAX_CHARGE_CURRENT_AMPERE = "max-charge-current-ampere";
    public static final String CHANNEL_CHARGING = "charging-switch";

    // climate channel ids
    public static final String CHANNEL_CLIMATE_STATE = "climate-state";
    public static final String CHANNEL_TARGET_TEMPERATURE = "target-temperature";
    public static final String CHANNEL_ESTIMATED_REACH_TARGET_TEMPERATURE_AT = "estimated-reach-target-temperature-at";
    public static final String CHANNEL_WITHOUT_EXTERNAL_POWER = "without-external-power";
    public static final String CHANNEL_AT_UNLOCK = "at-unlock";
    public static final String CHANNEL_WINDOW_HEATING_ENABLED = "window-heating-enabled";
    public static final String CHANNEL_WINDOW_HEATING_FRONT = "window-heating-front";
    public static final String CHANNEL_WINDOW_HEATING_REAR = "window-heating-rear";
    public static final String CHANNEL_AIR_CONDITIONING = "air-conditioning-switch";

    // auxiliary heating channel ids
    public static final String CHANNEL_AUXILIARY_HEATING_STATE = "auxiliary-heating-state";
    public static final String CHANNEL_AUXILIARY_START_MODE = "start-mode";
    public static final String CHANNEL_AUXILIARY_DURATION = "duration";
    public static final String CHANNEL_AUXILIARY_TARGET_TEMPERATURE = "auxiliary-target-temperature";
    public static final String CHANNEL_AUXILIARY_ESTIMATED_REACH_TARGET_AT = "auxiliary-estimated-reach-target-at";
    public static final String CHANNEL_AUXILIARY_HEATING = "auxiliary-heating-switch";

    // active ventilation channel ids
    public static final String CHANNEL_ACTIVE_VENTILATION_STATE = "active-ventilation-state";
    public static final String CHANNEL_ACTIVE_VENTILATION_DURATION = "active-ventilation-duration";
    public static final String CHANNEL_ACTIVE_VENTILATION = "active-ventilation-switch";

    // charging profile channel ids (the group also reuses target-state-of-charge, max-charge-current,
    // auto-unlock-plug and last-updated)
    public static final String CHANNEL_PROFILE_NAME = "name";
    public static final String CHANNEL_AT_PROFILE_LOCATION = "at-location";
    public static final String CHANNEL_NEXT_CHARGING_TIME = "next-charging-time";
    public static final String CHANNEL_MIN_STATE_OF_CHARGE_ENABLED = "min-state-of-charge-enabled";
    public static final String CHANNEL_MIN_STATE_OF_CHARGE = "min-state-of-charge";

    // shared "last updated" channel id used in status/odometer/fuel/charging/climate groups
    public static final String CHANNEL_LAST_UPDATED = "last-updated";
}
