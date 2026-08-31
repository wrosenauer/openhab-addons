# MyŠkoda Binding

This binding integrates Škoda connected cars through the **official MyŠkoda public API**
(`https://public.api.connect.skoda-auto.cz`), released by Škoda in 2026 as the successor to the
previously unofficial, reverse-engineered MySkoda/Škoda Connect API.

## Limitations

The official API is intentionally narrow. Compared to the MySkoda mobile app (or the older
unofficial API), this binding currently **cannot**:

- lock or unlock the vehicle
- honk & flash
- read trip statistics or maintenance/health data
- change charging settings (target state of charge, charge mode, preferred charging times, ...) -
  these are read-only here; only starting/stopping charging itself is supported
- receive live push updates - there is no MQTT/WebSocket channel, only polling

It **can** report vehicle status (doors, windows, lights, lock state), odometer, fuel/range
(combustion and hybrid), parking position, charging status and settings, air conditioning,
auxiliary heating and active ventilation, and it can start/stop charging, air conditioning,
auxiliary heating and active ventilation.

The API key is rate-limited to **20 requests/hour**, shared by every vehicle that uses the same
key. There is no push channel, so this binding polls; keep the refresh interval conservative,
especially if several vehicles share one API key. A command (e.g. starting charging) also
consumes one request, but the binding does **not** automatically re-poll after sending a
command - the updated state shows up on the vehicle's next scheduled poll.

## Supported Things

| Thing type | Description                                                                 |
|------------|------------------------------------------------------------------------------|
| `account`  | Bridge holding one MySkoda API key. One key can cover several vehicles.      |
| `vehicle`  | A single Škoda vehicle (VIN), configured under an `account` bridge.          |

## Obtaining an API Key

1. Open the MySkoda app, go to **Settings > Developer > API keys**.
1. Create a new key and select which vehicle(s) it should cover.
1. Copy the key into the `account` bridge's `apiKey` configuration parameter.

Keys expire; the binding surfaces the last known expiry as the `apiKeyExpiresAt` property on the
`account` bridge. There is no refresh-token mechanism - when a key expires, generate a new one in
the app and update the bridge configuration.

## Bridge Configuration

| Parameter | Type | Description                                                    |
|-----------|------|------------------------------------------------------------------|
| `apiKey`  | text | The MySkoda API key, generated in the app. Required.             |

## Thing Configuration

| Parameter        | Type    | Description                                                                 |
|-------------------|---------|-------------------------------------------------------------------------------|
| `vin`              | text    | Vehicle Identification Number. Required.                                     |
| `refreshInterval`  | integer | Polling interval in minutes. Default `15`.                                   |
| `sPin`             | text    | Vehicle security PIN, only required to start auxiliary heating.              |

## Channels

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
| `charge-power` | Number:Power | Current charge power. |
| `charge-rate` | Number:Speed | Charge rate in distance/hour. |
| `remaining-time` | Number:Time | Remaining time to fully charged. |
| `fully-charged-at` | DateTime | Estimated time fully charged. |
| `target-state-of-charge`, `battery-care-mode-target`, `preferred-charge-mode`, `charging-care-mode`, `auto-unlock-plug`, `max-charge-current`, `max-charge-current-ampere` | various | Read-only charging settings - not changeable through this API. |
| `charging-switch` | Switch | Start/stop charging. |
| `last-updated` | DateTime | When charging status was last reported. |

### Climate (`climate`)

| Channel | Type | Description |
|---------|------|--------------|
| `climate-state` | String | Air conditioning state. |
| `target-temperature` | Number:Temperature | Target cabin temperature, used when starting the air conditioning. |
| `estimated-reach-target-temperature-at` | DateTime | Estimated time the target temperature is reached. |
| `without-external-power`, `at-unlock` | Switch | Air conditioning settings (read-only). |
| `window-heating-enabled`, `window-heating-front`, `window-heating-rear` | various | Window heating state (read-only). |
| `air-conditioning-switch` | Switch | Start/stop the air conditioning using `target-temperature`. |
| `last-updated` | DateTime | When climate status was last reported. |

### Auxiliary Heating (`auxiliaryHeating`)

| Channel | Type | Description |
|---------|------|--------------|
| `auxiliary-heating-state` | String | Auxiliary heating state. |
| `start-mode` | String | Mode the heater last started in (read-only). |
| `duration` | Number:Time | Duration to run for when started. |
| `auxiliary-target-temperature` | Number:Temperature | Target cabin temperature. |
| `auxiliary-estimated-reach-target-at` | DateTime | Estimated time the target temperature is reached. |
| `auxiliary-heating-switch` | Switch | Start/stop auxiliary heating. Requires `sPin` to be configured on the vehicle thing. |

### Active Ventilation (`activeVentilation`)

| Channel | Type | Description |
|---------|------|--------------|
| `active-ventilation-state` | String | Active ventilation state. |
| `active-ventilation-duration` | Number:Time | Duration it runs for when started (read-only). |
| `active-ventilation-switch` | Switch | Start/stop active ventilation. |

## Full Example

### `myskoda.things`

```java
Bridge myskoda:account:home "MySkoda Account" [ apiKey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" ] {
    Thing vehicle mycar "My Škoda" [ vin="TMBJJ9NX0N1234567", refreshInterval=15 ]
}
```

### `myskoda.items`

```java
Switch      MySkoda_Locked            "Locked"                { channel="myskoda:vehicle:home:mycar:status#locked" }
Number:Length MySkoda_Mileage         "Mileage [%.0f %unit%]" { channel="myskoda:vehicle:home:mycar:odometer#mileage" }
Number:Dimensionless MySkoda_SoC      "State of Charge [%.0f %unit%]" { channel="myskoda:vehicle:home:mycar:charging#state-of-charge" }
Switch      MySkoda_Charging          "Charging"              { channel="myskoda:vehicle:home:mycar:charging#charging-switch" }
Number:Temperature MySkoda_TargetTemp "Target Temperature [%.1f %unit%]" { channel="myskoda:vehicle:home:mycar:climate#target-temperature" }
Switch      MySkoda_AirConditioning   "Air Conditioning"      { channel="myskoda:vehicle:home:mycar:climate#air-conditioning-switch" }
Location    MySkoda_Location          "Location"              { channel="myskoda:vehicle:home:mycar:position#location" }
```
