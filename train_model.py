import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error
import pickle

# ---------------------------
# 1. LOAD DATA
# ---------------------------
sim = pd.read_csv("simulations.csv")
cars = pd.read_csv("cars.csv")
tires = pd.read_csv("tires.csv")
brakes = pd.read_csv("brakes.csv")
roads = pd.read_csv("roads.csv")

# ---------------------------
# 2. MERGE FIRST (FIXED ORDER)
# ---------------------------
df = sim.merge(cars, left_on="car_id", right_on="id", suffixes=("", "_car"))
df = df.merge(tires, left_on="tire_id", right_on="id", suffixes=("", "_tire"))
df = df.merge(brakes, left_on="brake_id", right_on="id", suffixes=("", "_brake"))
df = df.merge(roads, left_on="road_condition_id", right_on="id", suffixes=("", "_road"))

# ---------------------------
# 3. CLEAN TARGETS (CRITICAL FIX)
# ---------------------------
df = df.replace([np.inf, -np.inf], np.nan)

df = df.dropna(subset=["total_distance", "total_time"])

# remove insane outliers (VERY IMPORTANT for physics sims)
df = df[df["total_distance"] < 1e6]
df = df[df["total_time"] < 1e5]

df = df.reset_index(drop=True)

# ---------------------------
# 4. FEATURES
# ---------------------------
X = df[
    [
        "speed_kmh",
        "tire_tread_mm",
        "slope_percent",
        "reaction_time",
        "has_abs",
        "weight_kg",
        "base_grip",
        "efficiency"
    ]
].copy()

# road feature safe add
if "road_grip" in df.columns:
    X["road_grip"] = df["road_grip"].fillna(1.0).values
elif "grip" in df.columns:
    X["road_grip"] = df["grip"].fillna(1.0).values
else:
    X["road_grip"] = 1.0

# ---------------------------
# 5. TARGET
# ---------------------------
y = df[["total_distance", "total_time"]]

# ---------------------------
# 6. FINAL SAFETY CHECK
# ---------------------------
X = X.replace([np.inf, -np.inf], np.nan).fillna(0)
y = y.replace([np.inf, -np.inf], np.nan).dropna()

# align X after y drop
X = X.loc[y.index]

# ---------------------------
# 7. TRAIN
# ---------------------------
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42
)

model = RandomForestRegressor(
    n_estimators=200,
    random_state=42,
    n_jobs=-1
)

model.fit(X_train, y_train)

# ---------------------------
# 8. EVAL
# ---------------------------
pred = model.predict(X_test)
error = mean_absolute_error(y_test, pred)

print("MAE:", error)

# ---------------------------
# 9. SAVE
# ---------------------------
with open("model.pkl", "wb") as f:
    pickle.dump(model, f)

print("Saved OK")