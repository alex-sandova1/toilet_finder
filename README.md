# DriverAssist

### Route Support Platform for Delivery Drivers

## Overview

## Why DriverAssist?

Delivery drivers operate under strict schedules and often struggle to locate reliable restroom facilities without wasting valuable delivery time.

Existing navigation apps can locate nearby businesses but cannot answer important questions such as:

- Is there actually a restroom?

- Is it clean?

- Is it currently accessible?

- Do I need to be a customer?

- Is there easy parking for delivery vehicles?

DriverAssist combines Google Maps, community reporting, and navigation into a single application designed specifically for delivery drivers.

## Core Features

### Map-Based Search & Discovery
- **Real-time Map Display**: Integrated Google Maps with smooth camera animations and live location tracking
- **Smart Category Filtering**: Search by restroom type (Public, Fast Food, Gas Station, Coffee Shop, Restaurant, Bar, Mall)
- **Dynamic Area Search**: Refresh results as you move the map with "Search this area" button
- **Auto-Discovery**: Automatically finds and displays nearby restrooms on app launch
- **Marker Clustering**: Visual markers for Google Places and community-added restrooms (different colors)

### Community Feedback System
- **Cleanliness Ratings**: 1-5 scale rating for long-term cleanliness likelihood
- **Real-time Status Alerts**: "Dirty now" or "Closed now" reports with auto-expiring status (3 hours dirty, 2 hours closed)
- **Aggregate Analytics**: 
  - Average cleanliness score
  - Dirty likelihood percentage
  - Historical dirty/closed report counts
  - Suggested category overrides from community
- **User Notes**: Add location-specific details (e.g., access codes, directions, amenities)
- **Verified Clean Filter (Premium)**: Hide poorly-rated (< 3.0) or currently dirty restrooms

### Custom Restrooms
- **Add to Map**: Long-press to add custom restroom with name, category, and notes
- **Community Contribution**: Custom restrooms sync to Firestore for all users
- **Offline Persistence**: Saved locally in Room database, accessible without internet
- **Edit/Delete**: Manage custom restrooms from feedback dialog

### Navigation
- **Nearest Restroom Button**: One-tap to find closest valid restroom and open Google Maps navigation
- **Multi-Candidate Logic**: Tries up to 6 nearest candidates to find a routable destination
- **Direct Integration**: Launches native Google Maps app for turn-by-turn driving directions
- **Location Validation**: Filters out invalid coordinates to avoid dead ends

### User System
- **Google Sign-In**: Secure authentication via Firebase
- **User Profiles**: Free and Verified (Premium $2/mo) tiers with profile persistence
- **Verified Benefits**: Access to Verified Clean filter (hide dirty restrooms)

### Offline Support
- **Local Database**: Room database for storing custom restrooms offline
- **Sync Strategy**: Custom restrooms saved locally, synced to Firestore when online
- **Offline Access**: Browse saved custom restrooms without internet
- **Smart Fallback**: Graceful degradation when APIs unavailable

## Technical Stack

**Frontend:**
- **UI Framework**: Jetpack Compose (Material 3)
- **State Management**: State-managed ViewModels + Compose State
- **Navigation**: Direct Intent-based navigation to Google Maps

**Backend:**
- **Authentication**: Firebase Authentication (Google Sign-In)
- **Database**: Firestore for community feedback, Room for offline cache
- **APIs**: Google Maps SDK, Places API (New SDK v5.2.0), Directions API

**Architecture:**
- **Pattern**: MVVM-lite with Repositories and ViewModels
- **Async**: Kotlin Coroutines, Flow, viewModelScope
- **Dependency Injection**: Manual DI via singletons (Firebase, Room)

**Build & Security:**
- **Kotlin**: 2.2.10
- **Compose BOM**: 2026.02.01
- **API Security**: Secrets Gradle Plugin, `local.properties` API keys
- **Permissions**: Fine/Coarse Location, Internet

## Recent Fixes & Improvements (Current Session)

### ✅ Completed
- **Nearest Restroom Navigation**: Refactored from complex route preview to direct Google Maps navigation
- **Multi-Candidate Selection**: Tries multiple nearby restrooms, avoids routing dead ends
- **Coordinate Validation**: Filters out invalid/default coordinates (0,0) and bounds checking
- **Maps SDK Initialization**: Async safe initialization with legacy renderer fallback
- **Error Visibility**: Search failures show user toasts instead of silent failures
- **Diagnostic Logging**: Comprehensive logs for debugging (Route, Search, Navigation tags)
- **Route Timeout Protection**: 15-second timeout per candidate to prevent hangs
- **Walking Fallback Removal**: Drives-only navigation (no walking mode)

### Architecture Simplifications
- Removed complex route preview UI and async route-fetching
- Added `findAndNavigateToNearestRestroom(context)` helper function
- Eliminated unnecessary state variables (activeRoute, activeRouteDestinationLocation)
- Streamlined button behavior for reliability

## Security & Prerequisites

### Before Running
1. **Google Cloud Console Setup**:
   - Create a project with Maps, Places, and Directions APIs enabled
   - Enable billing (required for all APIs)
   - Create API key with restrictions (Android app)
   - Get SHA-1 fingerprint: `./gradlew app:signingReport`

2. **Firebase Setup**:
   - Create Firebase project at firebase.google.com
   - Enable Google Sign-In authentication
   - Download `google-services.json` and save to `app/`

3. **Local Configuration**:
   - Add to `local.properties`: `MAPS_API_KEY=your_api_key_here`

### Requirements
- **Minimum SDK**: Android 7.0 (API 24)
- **Permissions**: 
  - `ACCESS_FINE_LOCATION` (precise GPS)
  - `ACCESS_COARSE_LOCATION` (network-based)
  - `INTERNET`

### API Keys
- **Hardcoding**: Never commit API keys—use `local.properties` and Secrets Gradle Plugin
- **Restrictions**: Restrict API keys to Android apps with SHA-1 fingerprint

## Testing

### Testing Offline Mode
**Emulator:**
```bash
# Disable network
adb emu gsm data off

# Re-enable network
adb emu gsm data on

# Or use Extended Controls: Settings → Network → Disconnect
```

**Device:**
- Toggle Airplane mode ON/OFF
- Or disable Wi-Fi + Mobile data

**What to test offline:**
- ✅ View custom restrooms you added (persist from local Room database)
- ✅ Add new custom restrooms (stored locally, sync when online)
- ❌ Search Google Places (requires live API)
- ❌ View community feedback (not cached)

### Viewing Local Database
```bash
adb shell
sqlite3 /data/data/com.example.driverassist/databases/restroom_database
> SELECT * FROM offline_restrooms;
```

## Known Limitations

- **Route Preview Removed**: Nearest restroom uses direct Google Maps navigation (simpler, more reliable)
- **Community Feedback Offline**: Only custom restrooms cached locally; Firestore feedback requires internet
- **Payment Mocked**: Verified User upgrade shows mock UI (no real transactions)
- **Maps Startup**: Initial load may timeout on some devices/builds (mitigated with renderer fallback)
- **Search Results Not Cached**: Google Places search requires live connectivity

## Roadmap

### Completed ✅
- [x] Google Sign-In authentication
- [x] Firestore community feedback system
- [x] Cleanliness ratings & dirty/closed alerts
- [x] Custom restroom creation & deletion
- [x] Verified Clean premium filter
- [x] Offline caching for custom restrooms
- [x] Direct Google Maps navigation
- [x] Multi-candidate nearest restroom selection
- [x] Driving-only navigation mode

### Planned ����
- [ ] Actual payment gateway for Verified upgrades
- [ ] Photo upload for restroom entrances/reviews
- [ ] Favorites/Bookmarks system
- [ ] Distance/time display on map markers
- [ ] Amenities filtering (WiFi, ADA, baby changing, paper towels)
- [ ] User contribution leaderboard
- [ ] Search result caching for offline access
- [ ] Restroom hours/availability tracking
- [ ] Rating confidence (helpful vote counts)
- [ ] Comprehensive unit & instrumentation testing

## Architecture Overview

```
MapScreen (Composable UI)
    ↓
MapViewModel (State + Actions)
    ↓
┌─────────────────────────────────┐
│  RestroomFeedbackRepository     │ → Firestore (Community feedback)
│  UserRepository                 │ → Firestore (User profiles)
│  RestroomDao                    │ → Room DB (Offline custom restrooms)
└─────────────────────────────────┘
    ↓
Google APIs (Maps, Places, Directions)
```

## Getting Started

```bash
# 1. Clone and open in Android Studio
git clone <repo>
cd toilet_finder

# 2. Set up local.properties
echo "MAPS_API_KEY=your_key_here" >> local.properties

# 3. Add google-services.json to app/

# 4. Build and run
./gradlew build
# In Android Studio: Run → Run 'app'
```

## Contributing

To add features:
1. **UI**: Add Composable in `MapScreen.kt`
2. **Logic**: Add function in `MapViewModel.kt`
3. **Data**: Add methods in `RestroomFeedbackRepository.kt`, `UserRepository.kt`, or `RestroomDao.kt`
4. **Models**: Update `RestroomModels.kt` if needed
5. **Tests**: Add unit tests in `src/test/`, UI tests in `src/androidTest/`

## License

[Add your license here]

## Support

For issues or questions, please open an issue on GitHub or contact the development team.
