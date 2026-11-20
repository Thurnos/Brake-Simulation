# Braking engine (Phase 1)

A time-stepped, straight-line braking simulation in `thurnos.brake_simulation.engine`.
It has no Spring, JPA or Lombok dependencies, so it can be unit-tested and reused anywhere.
The old `BrakingPhysicsEngine` and `/api/simulations/execute` are untouched.

## Try it

```
POST /api/engine/simulate?carId=1&tireId=1&brakeId=1&roadId=2
     &speed=100&tread=6&slope=0&abs=true&driverCondition=NORMAL&obstacle=70
```

Optional `obstacle` (metres ahead) adds an impact speed to the result. `trace=false` drops the time series.
The response holds the summary (stop distance, stop time, reaction and braking distance, peak deceleration,
impact speed) and a `trace` of points every 0.02 s with speed, position, deceleration, pedal, per-axle brake
force, per-axle load and lock flags. A car that cannot stop returns `stopped: false` and `null` distances.

From code:

```java
Scenario s = ScenarioFactory.fromTableValues(1400, 1.1, 1.0, 1.0, 8, 0, true, 1.3, 100, null);
SimulationResult r = new BrakingEngine().simulate(s);
```

## What happens each 1 ms step

1. **Driver.** No brake force until perception + foot movement time has passed (`DriverSpec`).
2. **Pedal.** Force builds linearly to full over `BrakeSpec.pedalRiseTimeS` (default 0.15 s).
3. **Demand.** `pedal × maxForceFactor × m × g`, split front/rear by `frontBias`.
4. **Load transfer.** `ΔFz = h · (F_brake + F_drag) / wheelbase` moves load to the front axle.
   Front and rear loads are solved together with the brake force (a few fixed-point passes).
5. **Grip limit per axle.** `μ_eff · Fz`, where `μ_eff` drops slightly as an axle gets heavier than at rest
   (`TireSpec.loadSensitivity`).
6. **Over the limit.** With ABS the axle keeps `absEfficiency` (0.95) of the limit. Without ABS the wheels
   lock and keep `lockedFrictionRatio` (0.8) of it, and stay locked.
7. **Resistances.** Drag `½ρ·Cd·A·v²`, rolling resistance `Crr·m·g·cosθ`, slope `m·g·sinθ`
   (positive slope = downhill, as before).
8. **Advance.** Deceleration is constant inside a step and position advances exactly
   (`x += v·dt − ½·a·dt²`). The pedal is sampled at mid-step.

## How it is checked

`BrakingEngineTest` compares against closed-form results wherever one exists:

- constant deceleration reproduces `v²/2a` (and reaction distance `v·t`)
- a linear force ramp costs exactly `v·T/2 − a·T²/24` extra metres
- grip-limited with ABS gives `absEfficiency · μ · g`; locked gives `0.8 · μ · g`
- slope uses `sin`/`cos`: `0.95·μ·g·cosθ − g·sinθ`
- grip-limited braking distance does **not** depend on mass or CoG height (physically correct)
- results agree across step sizes 0.5 ms, 1 ms and 5 ms
- dry 100–0 km/h with good tires lands in the 33–42 m range road tests report

## Known stand-ins (replaced in later phases)

| Stand-in | Where | Replaced by |
| --- | --- | --- |
| One peak μ per tire on a surface | `TireSpec` | slip-curve tire model (Phase 2) |
| ABS as a fixed 95% of peak grip | `EngineConfig.absEfficiency` | ABS control loop (Phase 2) |
| Brake force = `1.3 × efficiency × weight` | `BrakeSpec.fromEfficiency` | pedal → pressure → caliper → torque |
| Tread factor 0.5–1.0, same on every surface | `ScenarioFactory.treadFactor` | wet-aware tread and hydroplaning (Phase 3) |
| Wheelbase, CoG height, Cd, area, Crr guessed from mass | `VehicleSpec.estimate` | real columns on the `cars` table |
| No brake heating | | thermal model (Phase 4) |

Treat outputs as plausible estimates for exploring ideas, not for real-world safety decisions.
