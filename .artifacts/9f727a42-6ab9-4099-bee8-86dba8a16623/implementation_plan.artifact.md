# Fix [RequestTokenManager] getToken() -> BAD_AUTHENTICATION

The error `[RequestTokenManager] getToken() -> BAD_AUTHENTICATION` for the service `oauth2:https://www.googleapis.com/auth/cclog` is a common issue in Android apps using Google Play Services (specifically Maps and Places SDKs). It occurs when the SDK tries to send internal telemetry/logging data but fails to authenticate because the app's signature (SHA-1) and package name are not registered as an Android OAuth client in the Google Cloud Console.

## User Review Required

> [!IMPORTANT]
> This issue usually requires a configuration change in the **Google Cloud Console** rather than just a code change. You must ensure your app's SHA-1 fingerprint is registered.

1.  **Register Android Client ID**: Go to the [Google Cloud Console](https://console.cloud.google.com/), select your project (`driver-assist-5ce9c`), and under **APIs & Services > Credentials**, create an **OAuth 2.0 Client ID** for **Android** using your package name `com.example.driverassist` and your SHA-1 fingerprint.
2.  **Verify SHA-1**: The app already prints the SHA-1 to Logcat using `printSigningFingerprint(this)` in `MainPage.kt` and `LoginActivity.kt`. Use that value.

## Proposed Changes

While the root cause is external configuration, we can improve the app's resilience and reduce log noise by:
1.  Ensuring `GoogleSignInOptions` is fully configured.
2.  Checking for a valid Google account before certain SDK initializations if possible.
3.  Updating `google-services.json` (instructional).

### [Component Name] UI / Initialization

#### [MODIFY] [MainPage.kt](file:///Users/alexsandoval/AndroidStudioProjects/toilet_finder/app/src/main/java/com/example/driverassist/MainPage.kt)
- Move Places initialization to be more defensive.
- Add a check for account availability.

#### [MODIFY] [LoginActivity.kt](file:///Users/alexsandoval/AndroidStudioProjects/toilet_finder/app/src/main/java/com/example/driverassist/login/LoginActivity.kt)
- Ensure the `webClientId` is used correctly and optionally add `requestServerAuthCode` if needed for long-lived credentials (though `requestIdToken` is usually enough for Firebase).

## Verification Plan

### Manual Verification
1.  Run the app and check Logcat for the `BAD_AUTHENTICATION` error.
2.  If the error persists after registering the SHA-1 in Cloud Console, verify that the `google-services.json` has been updated to include the `oauth_client` section.
3.  Ensure Google Sign-In still works correctly.
