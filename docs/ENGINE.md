# Braking engine (Phases 1 to 3)

A time-stepped, straight-line braking simulation in `thurnos.brake_simulation.engine`.
It has no Spring, JPA or Lombok dependencies, so it can be unit-tested and reused anywhere.
The old `BrakingPhysicsEngine` and `/api/simulations/execute` are untouched.

## Try it

```
POST /api/engine/simulate?carId=1&tireId=1&brakeId=1&roadId=2
     &speed=100&tread=6&slope=0&abs=true&driverCondition=NORMAL&obstacle=70
```

Optional `obstacle` (metres ahead) adds an impact speed to the result. `trace=false` drops the time series.
Optional `water` (mm) sets the water film depth and overrides the road row; without it the engine uses
`road_conditions.water_depth_mm`, and if that is empty, 2 mm on wet-grip roads and none elsewhere.

The response holds the summary (stop distance, stop time, reaction and braking distance, peak deceleration,
impact speed, `brakeEnergyJ`, `tireSlipEnergyJ`) and a `trace` of points every 0.02 s with speed, position,
deceleration, pedal, per-axle tire force, per-axle load, per-axle **slip**, per-axle **brake pressure** (below 1
while ABS releases), lock flags and a `waterGripFactor` (1 = full grip, lower while hydroplaning). The summary also
holds `hydroplaningOnsetKmh` (null on a dry road). A car that cannot stop returns `stopped: false` and `null` distances.

From code:

```java
Scenario s = ScenarioFactory.fromTableValues(1400, 1.1, 1.0, 1.0, 8, 0, true, 1.3, 100, null);
SimulationResult r = new BrakingEngine().simulate(s);
```

## The model

Two axles, each with a spinning wheel:

```
wheel:  I · dω/dt = Fx · R − brakeTorque
tire:   Fx        = mu(slip) · Fz          slip = (v − ω·R) / v     (0 rolling, 1 locked)
car:    m · a     = Fx_front + Fx_rear + drag + rolling resistance − slope pull
```

- **Driver and pedal.** No force until perception + foot time has passed, then brake force builds over 0.15 s.
- **Weight transfer.** `ΔFz = h · (F_brake + F_drag) / wheelbase` moves load to the front axle; grip drops slightly as an axle gets more loaded (`loadSensitivity`).
- **Slip curve.** Friction rises to a peak at moderate slip, then falls toward a lower sliding value. Four shape families (dry, wet, snow, ice) are chosen from the road table's grip modifier; the peak height comes from tire grip × road × tread.
- **Without ABS** the wheel is pushed past the peak, runs away and locks, and the car decelerates at the (lower) sliding friction.
- **ABS** is a per-axle controller that sees only wheel speed. Every 5 ms it checks whether the wheel is slowing much faster than the car; if so it releases pressure, and re-applies once the wheel recovers. It runs at 10–14 Hz on all four surfaces, switches off below 1.5 m/s, and nothing in it knows the road's friction.
- **Rotating inertia.** The brakes also have to slow the spinning wheels (about 3% extra effective mass).
- **Other forces.** Drag `½ρ·Cd·A·v²`, rolling resistance `Crr·m·g·cosθ`, slope `m·g·sinθ` (positive = downhill).
- **Energy.** The engine reports heat into the brakes and energy lost to tire slip (the input for the Phase 4 thermal model).

### Water, tread and hydroplaning (Phase 3)

`WaterFilm(waterDepthMm, treadDepthMm, tirePressureKPa)` does two separate things, and only when there is water:

- **Wet grip from tread.** Peak friction falls from 1.0 (tread at 8 mm or more) to 0.7 (1.6 mm) once the film is
  about 0.5 mm deep. On dry, snow and ice roads tread depth changes nothing.
- **Hydroplaning.** The onset speed starts from the NASA/Horne result for a smooth tire on a flooded road,
  `V = 9·√p` knots with `p` in psi (94 km/h at 220 kPa). Tread raises it, `V × (1 + 0.5·tread/water)`, so grooves
  help a lot in shallow water and little in deep water; worn tires hydroplane earlier. Around that speed friction falls smoothly to
  10% of normal, and it comes back as the car slows, so a stop can start on water and finish on grip.

Onset speeds (km/h, 220 kPa):

| Water | Tread 8 mm | Tread 3 mm | Tread 1.6 mm |
| --- | --- | --- | --- |
| 2 mm | 282 | 165 | 132 |
| 5 mm | 169 | 122 | 109 |
| 8 mm | 141 | 112 | 104 |

Stopping distance including a 1.3 s reaction, ABS, 1400 kg, wet road (metres):

| Water / tread | 50 km/h | 80 | 100 | 120 | 140 |
| --- | --- | --- | --- | --- | --- |
| dry (any tread) | 28.5 | 54.8 | 75.9 | 99.8 | 126.4 |
| 2 mm / 8 mm | 34.1 | 68.3 | 96.6 | 129.0 | 165.7 |
| 2 mm / 1.6 mm | 40.2 | 85.1 | 127.7 | 190.9 | 288.0 |
| 5 mm / 8 mm | 34.2 | 68.6 | 97.8 | 134.0 | 180.7 |
| 5 mm / 1.6 mm | 40.3 | 87.8 | 142.0 | 234.4 | 376.2 |
| 8 mm / 1.6 mm | 40.5 | 89.2 | 148.8 | 252.8 | 407.3 |

Worn tread costs about 18% at 50 km/h and then grows sharply once the speed nears the onset speed. The distances at
the far right are what the simplified model says about driving fast on bald tires in standing water; the direction
is right, the exact numbers are not validated. Not modelled: viscous hydroplaning on thin films, water spray and
rubber-on-road temperature effects.

Data: add `water_depth_mm` to `road_conditions` (the column is created by `ddl-auto=update`; `roads.csv` has it too).
Optional extra row for flooded roads:

```sql
INSERT INTO road_conditions (name, grip_modifier, water_depth_mm) VALUES ('Standing water', 0.6, 6);
```

## Numerics

Wheel dynamics are stiff at low speed, so the wheel is advanced with a linearly implicit step, stable at the default 1 ms.
The tire force is evaluated at the car's predicted speed at the **end** of the step and averaged with the previous
step's end force. (Matching the wheel to the start-of-step speed left it one step behind and shaved about `a·dt/v`
off the slip every step: 0.9% of stopping distance at 1 ms, and a fading tail as the car came to rest. That bug was
found by comparing against an independent integration and is now covered by tests.)

## How it is checked

`BrakingEngineTest` uses closed-form results or conservation laws wherever one exists:

- gentle braking reproduces `v²/2a` with the wheels' inertia counted (81.18 m against a 81.15 m independent
  reference integrated at a 20 µs step; the closed form without wheel slip is 81.11 m)
- a linear force ramp costs `v·T/2 − a·T²/24` extra metres
- **energy balance:** brake heat + tire-slip energy equals the starting kinetic energy to within 0.1% on all four surfaces, with and without ABS
- the slip curve starts at zero, peaks at exactly `peakMu` and falls when locked, on every surface
- locked wheels decelerate at the sliding friction; slope uses `sin`/`cos`
- ABS reaches 88–98% of the best the tires can give on every surface, pulses at 5–25 Hz, and keeps slip below 0.6
- ABS beats locked wheels clearly on dry and wet roads, and stays out of the way when the brakes are the limit
- locked braking distance does not depend on mass or CoG height (physically correct)
- the brake-bias test: a front-heavy bias locks the front first, a rear-heavy bias the rear
- cars on ice come to rest instead of creeping; a car that cannot hold itself on a slope reports `stopped: false`
- results agree across step sizes from 0.5 ms to 5 ms
- dry 100–0 km/h with good tires lands in the 33–42 m range road tests report

`WaterFilmTest` (Phase 3):

- onset speed equals the published value (94.3 km/h for a smooth tire at 32 psi) and rises with tread and pressure and falls with water depth
- the grip factor falls monotonically with speed, is 1 far below the onset speed, and tends to 0.1 far above it
- tread depth changes a dry stop by exactly nothing, and a wet stop by over 20% at low speed
- a low-speed wet stop is not affected by the hydroplaning model; a high-speed one is lengthened clearly, more with worn tires
- grip returns later in the same stop as the car slows
- energy balance (resistances off) stays within 2% while hydroplaning

## What it shows

100 km/h, 1.3 s reaction, good tires, 1400 kg car (stopping distance including reaction):

| Surface | Old formula | New: ABS | New: locked wheels |
| --- | --- | --- | --- |
| Dry | 75.9 m | 72.9 m | 74.8 m |
| Wet | 93.0 m | 91.2 m | 98.2 m |
| Snow | 135.7 m | 122.9 m | 123.5 m |
| Ice | 235.3 m | 205.1 m | 199.6 m |

ABS helps clearly on wet roads and little on dry. On snow and ice it gives no advantage, which matches the
well-known real-world result that ABS can lengthen stops slightly on loose or very slippery surfaces. These
results come from illustrative curve shapes, so the pattern is more trustworthy than any single number.

## Known stand-ins (replaced in later phases)

| Stand-in | Where | Replaced by |
| --- | --- | --- |
| Four illustrative slip-curve shapes, not fitted to any real tire | `SurfaceKind` | fitted curves or per-tire data |
| Slip curve is quasi-static (no tire relaxation lag) | `TireSpec.SlipCurve` | relaxation-length model if needed |
| ABS thresholds tuned on a 1500 kg car; two-axle, not per-wheel | `AbsSettings` | per-wheel channels |
| Brake force = `1.3 × efficiency × weight` | `BrakeSpec.fromEfficiency` | pedal → pressure → caliper → torque |
| Hydroplaning is a smooth grip-loss curve around a simplified onset speed, not a fitted model (Gallaway/TTI style) | `WaterFilm` | fitted onset model if data exists |
| Tire pressure fixed at 220 kPa unless set in code | `WaterFilm.DEFAULT_PRESSURE_KPA` | a pressure column on `tires` |
| Wheelbase, CoG height, Cd, area, Crr, wheel size and inertia guessed from mass | `VehicleSpec.estimate` | real columns on the `cars` table |
| No brake heating | | thermal model (Phase 4) |

Treat outputs as plausible estimates for exploring ideas, not for real-world safety decisions.
