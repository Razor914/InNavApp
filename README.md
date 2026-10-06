# 📍 InNavApp

### Android-Based Indoor Navigation System Using WiFi Fingerprinting

**InNavApp** is an Android-based indoor navigation application that utilizes **WiFi Fingerprinting** to help users determine their location inside a building.

The application allows users to identify their position on a building floor plan based on WiFi signal information collected from available access points.

---

## 📌 Overview

GPS-based navigation systems work effectively in outdoor environments, but their accuracy can decrease significantly when used inside buildings.

To address this limitation, **InNavApp** was developed as an indoor positioning and navigation application using the **WiFi Fingerprinting** approach.

The application allows users to determine an estimated position on a digital floor plan based on previously collected WiFi signal characteristics.

This project was developed as an academic project in the **Information Systems program**.

---

## 🎯 Objectives

The main objectives of InNavApp are:

* Provide an indoor positioning solution using WiFi signals.
* Help users identify their estimated position inside a building.
* Display the estimated position on a building floor plan.
* Provide a foundation for indoor navigation without relying primarily on GPS.

---

## ✨ Features

### 📍 Indoor Position Detection

The application estimates the user's position based on WiFi signal information detected by the Android device.

### 🗺️ Digital Floor Plan

The application displays the building's floor plan and places the estimated user position on the corresponding area.

### 📶 WiFi Fingerprinting

WiFi signal characteristics are used as fingerprints to estimate the user's location.

### ☁️ Firebase Integration

Firebase is used as the application's database/backend service for storing the required positioning data.

### 🏢 Custom Building Map

The building floor plan can be uploaded and configured by the application developer according to the building being mapped.

---

## 🧠 How WiFi Fingerprinting Works

The basic concept implemented by the application can be illustrated as follows:

```text
        WiFi Access Points
                ↓
       Signal Strength Scan
                ↓
       WiFi Fingerprint
                ↓
       Compare with Dataset
                ↓
      Estimate User Position
                ↓
      Display on Floor Plan
```

### 1. Data Collection

WiFi signal information is collected from access points available at predefined locations inside the building.

### 2. Fingerprint Dataset

The collected signal characteristics are associated with known positions within the building.

### 3. Position Estimation

The application's positioning process compares the detected WiFi characteristics with the available fingerprint data.

### 4. Position Visualization

The estimated position is displayed on the building floor plan.

---

## 🛠️ Technology Stack

| Technology              | Usage                       |
| ----------------------- | --------------------------- |
| **Kotlin**              | Application development     |
| **Android**             | Mobile application platform |
| **Firebase**            | Backend / data storage      |
| **WiFi Fingerprinting** | Indoor positioning          |
| **Android Studio**      | Development environment     |
| **Git & GitHub**        | Version control             |

---

## 🏗️ System Concept

The system consists of several main components:

```text
┌──────────────────────┐
│      Android App     │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│    WiFi Scan Data    │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Fingerprint Dataset  │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Position Estimation  │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│    Floor Plan Map    │
└──────────────────────┘
```
---

## 📂 Project Structure

```text
InNavApp/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       ├── res/
│   │       └── AndroidManifest.xml
│   │
│   └── build.gradle
│
├── screenshots/
│   ├── home.png
│   ├── floor-plan.png
│   ├── wifi-scan.png
│   └── location-result.png
│
├── gradle/
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

Make sure the following tools are installed:

* Android Studio
* Android SDK
* JDK
* Android device with WiFi capability
* Firebase project configuration

### Installation

Clone the repository:

```bash
git clone https://github.com/Razor914/InNavApp.git
```

Open the project using **Android Studio**.

Configure the Firebase project according to the application's Firebase configuration.

Synchronize the Gradle dependencies and run the application on an Android device.

> Indoor positioning functionality may require a physical Android device because WiFi scanning behavior can differ from an emulator.

---

## 🎓 Academic Project

**Project:** InNavApp — Indoor Navigation System
**Program:** Information Systems
**Platform:** Android
**Positioning Method:** WiFi Fingerprinting
**Database/Backend:** Firebase
**Programming Language:** Kotlin

This project was developed as part of my academic project in the Information Systems program.

---

## 👨‍💻 Author

**Rafif Musyaffa Septiandra Tri Leksono**

Information Systems Graduate

GitHub: [@Razor914](https://github.com/Razor914)

---

⭐ Feel free to explore the repository and learn more about the project.
