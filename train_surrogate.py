"""
Fast surrogate of the physics engine.

Trains on engine_dataset.csv, which the Java engine writes from the repo's own tables:

    java -cp target/classes thurnos.brake_simulation.engine.DatasetGenerator . engine_dataset.csv

and saves surrogate.pkl. Unlike train_model.py (which learned the old formula and, because of a column-name
mismatch, always saw road_grip = 1.0), this model sees road grip, water depth and brake start temperature.

Run:  python train_surrogate.py
"""
import pickle
import sys

import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor, RandomForestRegressor
from sklearn.model_selection import KFold, cross_val_predict, train_test_split

FEATURES = [
    "speed_kmh", "tire_tread_mm", "slope_percent", "reaction_time", "has_abs", "weight_kg",
    "base_grip", "efficiency", "road_grip", "water_depth_mm", "brake_start_temp_c",
]
TARGETS = ["total_distance", "total_time", "braking_distance"]


def report(name, y_true, y_pred):
    rel = np.abs(y_pred - y_true) / np.maximum(np.abs(y_true), 1e-9)
    print(f"  {name:18s} median error {100*np.median(rel):5.1f}%   90th pct {100*np.quantile(rel, .9):5.1f}%"
          f"   worst {100*rel.max():6.1f}%   MAE {np.mean(np.abs(y_pred - y_true)):7.2f}")


def main(path="engine_dataset.csv", out="surrogate.pkl"):
    df = pd.read_csv(path)
    df = df[df["stopped"] == 1].dropna(subset=TARGETS).reset_index(drop=True)
    print(f"{len(df)} stopped runs from {path}")

    X, y = df[FEATURES], df[TARGETS]
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42)

    candidates = {
        "random forest": lambda: RandomForestRegressor(n_estimators=300, random_state=42, n_jobs=-1),
        "gradient boosting": lambda: HistGradientBoostingRegressor(max_iter=500, learning_rate=0.05,
                                                                    random_state=42),
    }
    best_name, best_score, best_models = None, np.inf, None
    for name, make in candidates.items():
        # Distances span 10 m to over 1 km, so fit log targets: errors then count in relative terms.
        models = {t: make().fit(X_tr, np.log(y_tr[t])) for t in TARGETS}
        print(name)
        score = 0
        for t in TARGETS:
            pred = np.exp(models[t].predict(X_te))
            report(t, y_te[t].to_numpy(), pred)
            score += np.median(np.abs(pred - y_te[t].to_numpy()) / y_te[t].to_numpy())
        if score < best_score:
            best_name, best_score, best_models = name, score, models

    print(f"keeping: {best_name}")
    # refit the winner on all data
    make = candidates[best_name]
    final = {t: make().fit(X, np.log(y[t])) for t in TARGETS}
    with open(out, "wb") as f:
        pickle.dump({"features": FEATURES, "targets": TARGETS, "models": final, "log_target": True}, f)
    print("saved", out)


def predict(bundle, rows):
    """rows: DataFrame with the FEATURES columns. Returns a DataFrame with the targets."""
    X = rows[bundle["features"]]
    return pd.DataFrame({t: np.exp(m.predict(X)) for t, m in bundle["models"].items()}, index=rows.index)


if __name__ == "__main__":
    main(*sys.argv[1:])
