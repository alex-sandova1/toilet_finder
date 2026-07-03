#   DriverAssist – Route Support Application

A mobile application designed to help delivery drivers quickly locate nearby restrooms and facilities during their routes.

## Current Features

### Map-Based Search
- Real-time map display using Google Maps
- Search for restrooms filtered by type (Public, Fast Food, Gas Station, Coffee Shop, Restaurant, Bar, Mall)
- Dynamic search by camera position with "Search this area" button
- Automatic location detection on app launch

### Restroom Discovery
- Integration with Google Places API for accurate restroom locations
- Support for 7 different restroom types/categories
- Proximity-based filtering (5km radius search)
- Display of up to 20 nearest results per search

### Navigation & Routing
- Walking route calculation using Google Maps Directions API
- Distance and estimated time display
- Integration with Google Maps app for turn-by-turn navigation
- "Find nearest restroom" functionality
- "Show route to nearest restroom" quick action

### Restroom Details
- Location name and address display
- Simulated cleanliness ratings (1-5 scale)
- Facility information (hours, code access requirements)
- Mock review text for reference

### Location & Permissions
- Fine and coarse location permission handling
- Fused location provider for accurate user positioning
- Camera animations and location tracking

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Maps**: Google Maps SDK for Android (Maps Compose)
- **Location Services**: Google Play Services Location
- **Places API**: Google Places SDK
- **Architecture**: State-managed Composables
- **Async**: Coroutines

## Current Limitations & Future Work

- **Mock Data**: Restroom reviews and ratings are simulated (not based on real user feedback)
- **No Authentication**: Login screen exists but does not implement actual user authentication
- **No Data Persistence**: Reviews and user interactions are not stored or synchronized
- **No User Feedback System**: The planned feedback feature (Used/Closed/Clean) is not yet implemented
- **Architecture**: Future improvements should include MVVM pattern and backend integration

## Prerequisites

- Android minSdk 24 (Android 7.0)
- Valid Google Maps API Key
- Valid Google Places API Key
- Location permissions enabled on device

## Roadmap

- [ ] Implement user authentication (Firebase or custom backend)
- [ ] Add backend database for real user reviews and ratings
- [ ] Implement user feedback collection system (cleanliness, access codes, hours)
- [ ] Add data persistence and synchronization
- [ ] Refactor MapScreen to follow MVVM architecture
- [ ] Implement comprehensive error handling and logging
- [ ] Add unit and instrumentation tests
- [ ] Secure API key management 
