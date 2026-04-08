import pandas as pd
import pickle

model = pickle.load(open("model.pkl", "rb"))

sample = pd.DataFrame([{
    "speed_kmh": 120,
    "tire_tread_mm": 5,
    "slope_percent": 3,
    "reaction_time": 0.8,
    "has_abs": 1,
    "weight_kg": 1500,
    "base_grip": 0.9,
    "efficiency": 0.85,
    "road_grip": 0.95
}])

print(model.predict(sample))