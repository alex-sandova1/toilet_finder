# DriverAssist – Route Support Application

A professional mobile application designed specifically for delivery drivers to quickly locate clean, accessible restrooms during their routes.

## Core Features

### Map-Based Search
- **Real-time Map Display**: Integrated Google Maps with smooth camera animations and location tracking.
- **Smart Filtering**: Search for restrooms by category (Public, Fast Food, Gas Station, Coffee Shop, Restaurant, Bar, Mall).
- **Dynamic Area Search**: Quickly refresh results as you move the map with the "Search this area" action.
- **Initial Discovery**: Automatically finds and displays the nearest restrooms upon app launch with a dedicated loading experience.

### Restroom Intelligence
- **Community Status**: Real-time alerts for restrooms reported as "Dirty" or "Closed" by the driver community.
- **Verified Clean Filter (Premium)**: One-tap filter to hide poorly rated or currently dirty restrooms, ensuring drivers only stop at vetted locations.
- **Custom Restrooms**: Drivers can add missing restrooms to the community map, including names, categories, and specific access notes.
- **Progressive Feedback**: A streamlined reporting system for rating cleanliness, adding location-specific notes (like access codes), or suggesting category updates.

### Navigation & Routing
- **Driving-Optimized Routes**: Directions calculated specifically for vehicles, providing accurate ETAs and paths for drivers on the road.
- **Seamless Integration**: One-tap "Navigate" button to launch turn-by-turn directions in the external Google Maps app.
- **Route Overlays**: In-app polyline display showing the direct path to the nearest restroom.

### User System
- **Secure Authentication**: Integrated Google Sign-In via Firebase Authentication.
- **User Profiles**: Persistent user profiles stored in Firestore, differentiating between Free and Verified (Premium) users.

## Technical Stack

- **UI**: Jetpack Compose (Material 3) with a modern TopAppBar and Scaffold-based layout.
- **Backend**: Firebase (Auth, Firestore) for real-time community data and user management.
- **Maps & Location**: Google Maps SDK, Places API (New SDK), and Fused Location Provider.
- **Architecture**: MVVM-lite pattern using state-managed ViewModels and structured Repositories.
- **Security**: API keys secured via the Secrets Gradle Plugin and stored in `local.properties`.
- **Async**: Kotlin Coroutines and Flow for non-blocking network and database operations.

## Security & Prerequisites

- **API Security**: No hardcoded API keys. All keys must be placed in `local.properties` (MAPS\_API\_KEY).
- **Permissions**: Requires Fine/Coarse Location and Internet access.
- **SDK**: Minimum Android SDK 24 (Android 7.0).

## Roadmap

- [x] Implement user authentication (Firebase)
- [x] Add backend database for real user reviews and ratings (Firestore)
- [x] Implement community feedback system (Cleanliness, Dirty/Closed alerts, Notes)
- [x] Secure API key management
- [x] Transition to Driving-only navigation for target audience
- [x] Implement "Verified Clean" premium filter
- [ ] Add offline support/caching for frequent routes
- [ ] Implement actual payment gateway for Verified User upgrades
- [ ] Add photo upload support for restroom entrances
- [ ] Comprehensive unit and instrumentation testing
