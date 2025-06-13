# AppLock v1.1.0 Release Notes

<div align="center">
  <img src="screenshots/ic_screenshot.png" alt="AppLock" height="200" />
  <h2>Stability & Security Update</h2>
  <p><em>June 10, 2025</em></p>
</div>

---

We're excited to announce the release of **AppLock 1.1.0**! This update includes significant
architectural improvements, enhanced security, and a better user experience.

## What's New

### Architecture & Performance

- **Migration to Clean Architecture**: Complete architectural refactoring for improved code
  separation, testability, and maintainability
- **Improved app loading and filtering**: Enhanced app list performance with optimized filtering
  algorithms
- **Enhanced battery optimization handling**: Reduced background service footprint with smarter
  state management

### Security & Privacy Improvements

- **Fixed system unlock conflict**: Resolved critical issue where app lock screen would incorrectly
  appear over the system unlock screen
- **Close stale password overlays (#3)**: Fixed security vulnerability by automatically closing
  password screens when different apps gain focus
- **Improved lock screen behavior and UI**: Enhanced verification to prevent unnecessary lock
  screens
  with improved user interface
- **Lock the app and remove lockscreen from recents**: Prevented unauthorized access through recent
  apps list

### User Experience

- **Separate SetPassword and ChangePassword screens**: Improved OEM settings navigation with better
  workflow
- **Improved animations**: Enhanced visual transitions throughout the app
- **Better first-time setup flow**: Updated version with more intuitive onboarding experience
- **Added Github link to settings**: Direct access to project repository for feedback and
  contributions

### Bug Fixes

- **Fixed crash in app list**: Resolved stability issues when loading and displaying applications
- **Updated app version to 1.1**: Version bump for this feature release
- **Various documentation improvements**: Updated README with more comprehensive information

## Getting Started

1. Download AppLock v1.1.0 from
   the [releases page](https://github.com/PranavPurwar/AppLock/releases)
2. If upgrading from v1.0.0, your existing settings and locked apps will be preserved
3. No additional configuration is required to benefit from the security improvements

## Permissions

AppLock requires the following permissions to function properly:

- **Usage Access**: To detect when protected apps are launched
- **Display Over Other Apps**: To show the authentication screen
- **Biometric**: For fingerprint/face recognition (optional)

## Future Plans

We're already working on the next update with additional features:

- Enhanced theming options
- Custom unlock patterns
- Scheduled protection periods
- Individual authentication methods per app
- App usage statistics

---

Thank you for choosing AppLock for your privacy needs. We welcome your feedback and suggestions
through GitHub issues!

<div align="center">
  <p><b>AppLock Team</b></p>
  <a href="https://github.com/PranavPurwar/AppLock">GitHub</a> | 
  <a href="https://github.com/PranavPurwar/AppLock/issues">Report Issues</a>
</div>
