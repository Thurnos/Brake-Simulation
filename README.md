#  Brake Simulation AI System

##  Project Overview

This project is a hybrid physics + machine learning system that simulates vehicle braking behavior and predicts braking distance and stopping time based on vehicle, tire, brake, and road conditions.

The system combines:
- A physics-based simulation engine for data generation
- A machine learning model trained on synthetic simulation data
- A Python-based API for real-time predictions
- A Spring Boot backend for system integration

---

##  Physics engine (new)

`thurnos.brake_simulation.engine` is a time-stepped braking engine: pedal ramp, weight transfer, wheel dynamics,
tire slip curves per surface, an ABS controller that only watches wheel speed, drag, slope, and an energy account
for the brakes, and (Phase 3) water film depth with hydroplaning and wet-only tread wear, and (Phase 4) brake heating, cooling and fade with a repeated-stops endpoint, plus (Phase 5) a calibration tool, an engine-trained surrogate model and a playback page at `/engine.html`. It returns a full speed / slip / force / pressure trace.
Endpoint: `POST /api/engine/simulate`. See [docs/ENGINE.md](docs/ENGINE.md) for the model, how it is checked and the
list of placeholders that later phases replace.

---

##  Goal of the Project

The main goal is to:

- Simulate realistic braking scenarios using physics rules
- Generate structured training data
- Train a machine learning model on that data
- Predict braking distance and stopping time instantly
- Integrate ML predictions into a backend system

This mimics real-world automotive safety and driver-assistance systems.

---

## Key Features

- Physics-based synthetic dataset generation
- Multi-table data merging:
  - Cars
  - Tires
  - Brakes
  - Road conditions
- Machine Learning regression model (Random Forest)
- REST API for predictions (FastAPI)
- Spring Boot backend integration
- Visualization-ready outputs for evaluation

---

##  Technologies Used

### Python Stack
- pandas
- numpy
- scikit-learn
- FastAPI
- matplotlib

### Java Stack
- Spring Boot
- RestTemplate
- Maven

---

## System Output

The model predicts:

- 🚗 Total braking distance (meters)
- ⏱️ Total stopping time (seconds)

Based on inputs such as:
- Speed
- Tire condition
- Road slope
- Reaction time
- Vehicle weight
- Brake efficiency

---

### System Architecture

### High-Level Flow

1. User sends simulation request (Spring Boot)
2. Spring Boot forwards request to Python ML API
3. FastAPI processes request
4. ML model predicts results
5. Response is returned to Spring Boot
6. Output is returned to client

---

### ⚙️ Architecture Diagram
User Request
↓
Spring Boot Backend
↓ (REST API call)
Python FastAPI Service
↓
ML Model (model.pkl)
↓
Prediction Output
↓
Spring Boot Response

---

## 🧩 System Components

### 1. Spring Boot Backend
- Handles user requests
- Validates input data
- Sends HTTP requests to Python ML service
- Returns final response to user

---

### 2. Python ML Service (FastAPI)
- Loads trained RandomForest model
- Accepts JSON input
- Converts input into DataFrame
- Returns predictions

---

### 3. Machine Learning Model
- Algorithm: RandomForestRegressor
- Trained on physics-generated simulation data
- Output:
  - braking distance
  - stopping time

---

## 📡 Data Flow (Step-by-Step)

1. Client sends simulation request:
   - speed
   - tire condition
   - slope
   - reaction time

2. Spring Boot receives request

3. Spring Boot sends POST request: http://localhost:8000/predict 
4. FastAPI:
- parses JSON
- formats input
- sends to ML model

5. ML model returns prediction

6. Response flows back to Spring Boot

7. Final result is returned to client

---

##  Architecture Diagram (Logical View)
            ┌──────────────┐
            │    Client    │
            └──────┬───────┘
                   │
                   ▼
         ┌──────────────────┐
         │  Spring Boot API │
         └──────┬───────────┘
                │ REST call
                ▼
    ┌──────────────────────────┐
    │ Python FastAPI Service   │
    └──────────┬───────────────┘
               │
               ▼
    ┌──────────────────────────┐
    │  ML Model (RandomForest) │
    └──────────┬───────────────┘
               ▼
     Prediction Output

---

##  Design Rationale

### Why Python + Java?

- Python:
  - Best ecosystem for machine learning
  - Fast model development and inference

- Java (Spring Boot):
  - Strong backend structure
  - Scalable API architecture
  - Production-ready service layer

---

##  Current Project Status

- [x] Physics simulation engine
- [x] Dataset generation
- [x] Machine learning model training
- [x] Model export (`model.pkl`)
- [x] Local prediction working
- [ ] FastAPI service integration
- [ ] Spring Boot full integration
- [ ] Visualization dashboard
- [ ] Deployment (Docker/cloud)

---

##  System Philosophy

Instead of solving physics equations in real time, the system learns the behavior and predicts outcomes instantly using machine learning.

This allows:
- Faster computation
- Scalable predictions
- Real-time response systems

---

##  Future Improvements

- Replace RandomForest with XGBoost / Neural Networks
- Add real-time WebSocket predictions
- Deploy ML service using Docker
- Add frontend dashboard (React)
- Improve physics simulation accuracy
- Add model retraining pipeline

---
