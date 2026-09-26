# MyŠkoda Binding

This binding integrates Škoda connected cars through the **official MyŠkoda Public API**
(`https://public.api.connect.skoda-auto.cz`), released by Škoda in 2026 as the successor to the
previously unofficial, reverse-engineered MySkoda/Škoda Connect API.
The official developer documentation is available at <https://public.api.connect.skoda-auto.cz/docs>.

The binding currently supports version **1.1.0** of the API contract.

## Limitations

The official API is intentionally narrow. Compared to the MyŠkoda mobile app (or the older
unofficial API), this binding currently **cannot**:

- lock or unlock the vehicle
- honk & flash
- read trip statistics or maintenance/health data
- change charging settings other than the target state of charge and the charge mode (e.g.
  battery care mode, maximum charge current or auto-unlock of the plug are read-only)
- edit the timers and preferred charging times of charging profiles (the profile settings -
  target state of charge, charge current, plug unlock, minimum state of charge - can be changed)
- change window heating or "air conditioning at unlock"
- receive live push updates - there is no MQTT/WebSocket channel, only polling

It **can** report vehicle status (doors, windows, lights, lock state), odometer, fuel/range
(combustion and hybrid), parking position, charging status and settings, air conditioning,
auxiliary heating and active ventilation. It can start/stop charging, air conditioning,
auxiliary heating and active ventilation, set the charging limit (target state of charge)
and charge mode, and change the settings of a charging profile.

Which data is available depends on what the vehicle supports. Parts a vehicle does not support
are simply not reported, and their channels stay `NULL`. Parts that could not be retrieved at
the time of a poll keep their last known values. A single value the vehicle does not report is
set to `UNDEF`.

## Rate Limits and Polling

Requests are rate-limited **per vehicle (VIN)**, currently to **20 requests per hour**. Škoda
states that this value is not final and may change; the binding follows the `RateLimit-*` and
`Retry-After` headers the API returns rather than a hard-coded limit. (The API changelog for
1.0.0 speaks of a limit "per key" - the binding tracks the quota per VIN as the documentation
describes, so one vehicle running out of requests does not block others on the same key.)

Every poll and every command counts against the quota, including requests that fail with a
server error. Requests rejected because of an invalid or expired key do not count.

There is no push channel, so the binding polls each vehicle every `refreshInterval` minutes.
The default of 15 minutes uses 4 of the 20 hourly requests and leaves the rest for commands.
The binding does **not** re-poll after sending a command - the updated state shows up on the
vehicle's next scheduled poll. When the quota is exhausted, the vehicle goes `OFFLINE` until the
quota replenishes.

## Supported Things

| Thing type | Description                                                                  |
|------------|------------------------------------------------------------------------------|
| `account`  | Bridge holding one MyŠkoda API key. One key can cover several vehicles.      |
| `vehicle`  | A single Škoda vehicle (VIN), configured under an `account` bridge.          |

The `account` bridge shows the key's expiry time as the `apiKeyExpiresAt` property. The
`vehicle` thing shows the vehicle's name and license plate as the `vehicleName` and
`licensePlate` properties.

## Discovery

There is no discovery. The API has no endpoint to list the vehicles an API key covers, so vehicle
things have to be added manually with their VIN.

## Obtaining an API Key

API keys are created and managed in the **MyŠkoda app**:

1. If you do not have the app yet, install it from <https://go.skoda.eu/myskoda> and sign in with
   your Škoda ID.
1. Open the API key management:
   - on the phone with the MyŠkoda app installed, open <https://go.skoda.eu/api-keys>, or
   - on a computer, open the [developer documentation](https://public.api.connect.skoda-auto.cz/docs)
     and scan the QR code shown under "Getting an API key" with your phone.
1. Create a new key and select the vehicle(s) it should cover.
1. Copy the key into the `apiKey` parameter of the `account` bridge.

A key only works for the vehicles selected when it was created, and it **expires**. The API has
no refresh mechanism - before the key expires (see the `apiKeyExpiresAt` property of the
bridge), create a new key in the app and update the bridge configuration. When the key has
expired, the bridge goes `OFFLINE` with a corresponding message.

## Bridge Configuration

| Parameter | Type | Description                                             |
|-----------|------|---------------------------------------------------------|
| `apiKey`  | text | The MyŠkoda API key, created in the app. Required.      |

## Thing Configuration

| Parameter         | Type    | Description                                                         |
|-------------------|---------|---------------------------------------------------------------------|
| `vin`             | text    | Vehicle Identification Number. Required.                            |
| `refreshInterval` | integer | Polling interval in minutes. Default `15`.                          |
| `sPin`            | text    | Vehicle security PIN, only required to start auxiliary heating.     |
| `chargingProfile` | text    | Charging profile shown in the `chargingProfile` group, by name or id. Empty (default): the profile of the location the vehicle is currently at. |

## Channels

Channels that can be changed:

- `charging#charging-switch`, `climate#air-conditioning-switch`,
  `auxiliaryHeating#auxiliary-heating-switch` and `activeVentilation#active-ventilation-switch`
  start or stop the respective function.
- `charging#target-state-of-charge` and `charging#preferred-charge-mode` change the vehicle's
  charging settings immediately (one request each).
- The settings in the `chargingProfile` group change the shown charging profile immediately (one
  request each).
- The start parameters described below are only sent together with a start command.

All other channels are read-only.

### Start Parameters

Some settings are not changed on their own but sent along with a start command: the target
temperature and "without external power" for the air conditioning, and the duration, start mode
and target temperature for the auxiliary heating. Changing one of these channels costs no API
request; the value is kept until the next start command sends it, even if a poll in between
still reports the vehicle's previous value.

### Status (`status`)

| Channel                 | Type   | Description                                        |
|--------------------------|--------|-----------------------------------------------------|
| `overall-doors-locked`   | String | Overall doors/trunk lock state.                     |
| `locked`                 | Switch | Whether the vehicle is locked.                      |
| `reliable-lock-status`   | String | Only reported by newer vehicles.                    |
| `doors`                  | String | Whether any door is open.                           |
| `windows`                | String | Whether any side window is open.                    |
| `lights`                 | String | Whether any light is on.                            |
| `sunroof`                | String | Sunroof state.                                      |
| `trunk`                  | String | Trunk state.                                        |
| `bonnet`                 | String | Bonnet state.                                       |
| `last-updated`           | DateTime | When the vehicle last reported its status.        |

### Odometer (`odometer`)

| Channel | Type | Description |
|---------|------|--------------|
| `mileage` | Number:Length | Total distance travelled. |
| `last-updated` | DateTime | When the odometer was last reported. |

### Fuel & Range (`fuel`)

Only reported for vehicles with a combustion engine, including hybrids.

| Channel | Type | Description |
|---------|------|--------------|
| `car-type` | String | Drivetrain type. |
| `adblue-range` | Number:Length | AdBlue range (diesel only). |
| `total-range` | Number:Length | Total range. |
| `primary-engine-type` / `secondary-engine-type` | String | Engine type (secondary is populated for plug-in hybrids). |
| `primary-state-of-charge` / `secondary-state-of-charge` | Number:Dimensionless | Engine state of charge. |
| `primary-fuel-level` / `secondary-fuel-level` | Number:Dimensionless | Engine fuel level. |
| `primary-range` / `secondary-range` | Number:Length | Engine range. |
| `last-updated` | DateTime | When fuel status was last reported. |

### Position (`position`)

| Channel | Type | Description |
|---------|------|--------------|
| `parking-state` | String | `PARKED` or `IN_MOTION`. |
| `location` | Location | GPS coordinates (only when parked). |
| `address` | String | Formatted address (only when parked). |

### Charging (`charging`)

Only reported for battery-electric and plug-in hybrid vehicles.

| Channel | Type | Description |
|---------|------|--------------|
| `state-of-charge` | Number:Dimensionless | Battery state of charge. |
| `remaining-range` | Number:Length | Remaining electric range. |
| `charging-state` | String | Charging state. |
| `charge-type` | String | `AC`, `DC` or `OFF`. |
| `plug-connection-state` | String | `CONNECTED` or `DISCONNECTED`. |
| `plug-lock-state` | String | `LOCKED` or `UNLOCKED`. |
| `charge-power` | Number:Power | Current charge power. |
| `charge-rate` | Number:Speed | Charge rate in distance/hour. |
| `remaining-time` | Number:Time | Remaining time to fully charged. |
| `fully-charged-at` | DateTime | Estimated time fully charged. |
| `target-state-of-charge` | Number:Dimensionless | Charging limit. Vehicles typically accept 50 to 100 % in steps of 10. |
| `preferred-charge-mode` | String | Charge mode, e.g. `MANUAL` or `TIMER`. Only the modes the vehicle reports as available are offered. |
| `battery-care-mode-target`, `charging-care-mode`, `auto-unlock-plug`, `max-charge-current`, `max-charge-current-ampere` | various | Read-only charging settings - not changeable through this API. |
| `charging-switch` | Switch | Start/stop charging. |
| `last-updated` | DateTime | When charging status was last reported. |

### Climate (`climate`)

| Channel | Type | Description |
|---------|------|--------------|
| `climate-state` | String | Air conditioning state. |
| `target-temperature` | Number:Temperature | Target cabin temperature, sent with the next air conditioning start. |
| `estimated-reach-target-temperature-at` | DateTime | Estimated time the target temperature is reached. |
| `without-external-power` | Switch | Whether the air conditioning may run without external power, sent with the next air conditioning start. |
| `at-unlock` | Switch | Whether the air conditioning starts when the vehicle is unlocked (read-only). |
| `window-heating-enabled`, `window-heating-front`, `window-heating-rear` | various | Window heating state (read-only). |
| `air-conditioning-switch` | Switch | Start/stop the air conditioning using `target-temperature`. |
| `last-updated` | DateTime | When climate status was last reported. |

### Auxiliary Heating (`auxiliaryHeating`)

| Channel | Type | Description |
|---------|------|--------------|
| `auxiliary-heating-state` | String | Auxiliary heating state. |
| `start-mode` | String | `HEATING` or `VENTILATION`, sent with the next auxiliary heating start. |
| `duration` | Number:Time | Duration to run for, sent with the next auxiliary heating start. |
| `auxiliary-target-temperature` | Number:Temperature | Target cabin temperature, sent with the next auxiliary heating start. |
| `auxiliary-estimated-reach-target-at` | DateTime | Estimated time the target temperature is reached. |
| `auxiliary-heating-switch` | Switch | Start/stop auxiliary heating. Requires `sPin` to be configured on the vehicle thing. |

### Active Ventilation (`activeVentilation`)

| Channel | Type | Description |
|---------|------|--------------|
| `active-ventilation-state` | String | Active ventilation state. |
| `active-ventilation-duration` | Number:Time | Duration it runs for when started (read-only - the API does not accept a duration for active ventilation). |
| `active-ventilation-switch` | Switch | Start/stop active ventilation. |

### Charging Profile (`chargingProfile`)

Charging profiles are the saved charging locations set up in the MyŠkoda app. The group shows
**one** profile: by default the profile of the location the vehicle is currently at, or the
profile set with the `chargingProfile` configuration parameter (useful to change e.g. the "Home"
profile while the vehicle is away). When the vehicle is not at a saved location and no profile is
configured, the channels are `UNDEF` and changes are rejected with a warning in the log.

| Channel | Type | Description |
|---------|------|--------------|
| `name` | String | Name of the profile. |
| `at-location` | Switch | Whether the vehicle is at the location of this profile. |
| `next-charging-time` | String | Next time (`HH:mm`, vehicle local time) charging is triggered by this profile; only while the vehicle is at its location. |
| `target-state-of-charge` | Number:Dimensionless | Target state of charge of this profile. |
| `max-charge-current` | String | `REDUCED` or `MAXIMUM`. |
| `auto-unlock-plug` | String | `PERMANENT` or `OFF`. |
| `min-state-of-charge-enabled` | Switch | Charge immediately, regardless of timers, while the battery is below `min-state-of-charge`. |
| `min-state-of-charge` | Number:Dimensionless | Battery level for immediate charging. |
| `last-updated` | DateTime | When the charging profiles were last reported. |

The API only accepts a charging profile as a whole. When one of these settings is changed, the
binding sends the complete profile as reported by the last poll with just that setting changed -
timers and preferred charging times are sent back unchanged. If the profile was changed in the
MyŠkoda app since the last poll, those changes are overwritten; wait for the next poll after
editing a profile in the app before changing it from openHAB.

`charging#target-state-of-charge` and `chargingProfile#target-state-of-charge` are different
settings: the first is the vehicle's current charging limit, the second the limit stored in the
profile, which the vehicle applies when it arrives at that location.

## Full Example

### `myskoda.things`

```java
Bridge myskoda:account:home "MySkoda Account" [ apiKey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" ] {
    Thing vehicle mycar "My Škoda" [ vin="TMBJJ9NX0N1234567", refreshInterval=15, chargingProfile="Home" ]
}
```

### `myskoda.items`

```java
Switch      MySkoda_Locked            "Locked"                { channel="myskoda:vehicle:home:mycar:status#locked" }
Number:Length MySkoda_Mileage         "Mileage [%.0f %unit%]" { channel="myskoda:vehicle:home:mycar:odometer#mileage" }
Number:Dimensionless MySkoda_SoC      "State of Charge [%.0f %unit%]" { channel="myskoda:vehicle:home:mycar:charging#state-of-charge" }
Number:Dimensionless MySkoda_Limit    "Charging Limit [%.0f %unit%]" { channel="myskoda:vehicle:home:mycar:charging#target-state-of-charge" }
Number:Dimensionless MySkoda_HomeLimit "Home Charging Limit [%.0f %unit%]" { channel="myskoda:vehicle:home:mycar:chargingProfile#target-state-of-charge" }
Switch      MySkoda_Charging          "Charging"              { channel="myskoda:vehicle:home:mycar:charging#charging-switch" }
Number:Temperature MySkoda_TargetTemp "Target Temperature [%.1f %unit%]" { channel="myskoda:vehicle:home:mycar:climate#target-temperature" }
Switch      MySkoda_AirConditioning   "Air Conditioning"      { channel="myskoda:vehicle:home:mycar:climate#air-conditioning-switch" }
Location    MySkoda_Location          "Location"              { channel="myskoda:vehicle:home:mycar:position#location" }
```

## Troubleshooting

| Symptom                                                        | Cause and fix                                                                                                                                              |
|----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Bridge `OFFLINE`, "API key has expired"                        | Create a new key in the MyŠkoda app and update the bridge's `apiKey`.                                                                                     |
| Vehicle `OFFLINE` with a configuration error after the first poll | The key does not cover this VIN (or is invalid). Check the VIN, or create a key that includes the vehicle.                                                |
| Vehicle `OFFLINE`, rate limit message                          | The vehicle's hourly quota is used up. It comes back online by itself; increase `refreshInterval` or send fewer commands.                                    |
| A command has no effect, warning in the log                    | The vehicle refused the operation (not supported, currently disabled, not authorized for your user, or temporarily not accepting requests). The log shows the reason. |
| `chargingProfile` channels are `UNDEF`, changes are rejected | The vehicle is not at a saved charging location, or no profile matches the `chargingProfile` parameter. Set the parameter to the profile's name as shown in the app (or its id). |
| `target-state-of-charge` is rejected                           | Most vehicles only accept 50 to 100 % in steps of 10; the log lists the values the vehicle accepts.                                                        |

## Upgrading

The binding has not been released as part of openHAB yet, so there are no version numbers; the
dates below refer to the entries in the [changelog](#changelog).

### From the 2026-08-31 Version

- **New channels are added automatically.** Existing `vehicle` things created in the UI get the
  new `charging#plug-connection-state` and `charging#plug-lock-state` channels and the
  `chargingProfile` group on the first start after the upgrade (through the binding's thing type
  update instructions). Things defined in
  `.things` files always use the current channel list anyway. Only if the channels are still
  missing, delete and re-add the `vehicle` thing.
- **New channel group `chargingProfile`** - also added automatically, like the plug channels.
- **Channels that became writable** (`target-state-of-charge`, `preferred-charge-mode`,
  `without-external-power`, `start-mode`) change without any action. Check your rules: a
  `sendCommand` to `target-state-of-charge` or `preferred-charge-mode` now **changes the
  vehicle's settings** and costs a request; use `postUpdate` if you only meant to change the
  item state.
- **Air conditioning without external power:** Until now the binding always started the air
  conditioning with "without external power" enabled. It now uses the vehicle's own setting (or
  the value set on `climate#without-external-power`). If that setting is off in your vehicle, the
  air conditioning no longer starts when the vehicle is not plugged in - switch the channel `ON`
  to get the old behavior.
- **Auxiliary heating start mode:** The auxiliary heating used to always start in `HEATING` mode.
  It now uses the mode reported by the vehicle (or the value set on `auxiliaryHeating#start-mode`).
- **Rate limit per vehicle:** The quota is now tracked per VIN instead of per API key. If you had
  increased `refreshInterval` because several vehicles share one key, you can lower it again.
- The new, optional `chargingProfile` parameter needs no action; no configuration parameters were
  renamed or removed.

## Changelog

### 2026-09-26

- Set the charging limit (`target-state-of-charge`) and the charge mode (`preferred-charge-mode`),
  using the new API endpoints `PUT /charging/limit` and `PUT /charging/mode`. The charge mode
  options are limited to the modes the vehicle reports as available.
- `without-external-power` (air conditioning) and `start-mode` (auxiliary heating) can be set and
  are sent with the next start command instead of fixed values.
- Start parameters (target temperatures, duration, start mode, without external power) set
  through a channel are no longer overwritten by a poll before they are sent.
- New `chargingProfile` channel group to view and change the settings of a charging profile
  (target state of charge, max charge current, plug auto-unlock, minimum state of charge), using
  `PUT /charging-profiles/{id}`, and new `chargingProfile` configuration parameter to select
  the profile.
- New channels `plug-connection-state` and `plug-lock-state` (API 1.1.0), and new `vehicle`
  properties `vehicleName` and `licensePlate`.
- The quota is tracked per VIN, as documented by the API, instead of per API key.
- A vehicle refusing an operation (`operation-not-authorized`, `vehicle-not-accepting-requests`)
  is no longer mistaken for an invalid API key or an exhausted quota.
- The retry time after `429 Too Many Requests` is taken from the `Retry-After` header.
- Error messages for rejected values list the values the API accepts.
- Documentation: API key instructions follow the official developer documentation, new sections
  on rate limits, troubleshooting and upgrading.

### 2026-08-31

- Initial version: `account` bridge and `vehicle` thing with status, odometer, fuel & range,
  position, charging, climate, auxiliary heating and active ventilation, plus start/stop of
  charging, air conditioning, auxiliary heating and active ventilation.
