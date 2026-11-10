# Sola OTA Release & Polling Guide

This guide explains how Sola's in-app Over-The-Air (OTA) updater works and how to publish new releases so that installed apps automatically detect, download, and install them.

---

## 1. How Sola Polls for Updates

Sola uses GitHub's public API without requiring any third-party update servers:

```
[Installed App: v1.0.0]
         |
         | Checks on startup (every 6h) or manual button click
         v
GET https://api.github.com/repos/sillypari/Sola/releases/latest
         |
         | Returns JSON payload
         v
+-------------------------------------------------------------+
| 1. Tag Comparison:                                          |
|    remote tag ("v1.0.1") > current BuildConfig ("1.0.0")   |
|                                                             |
| 2. Asset Discovery:                                         |
|    Scans assets[] for first file matching *.apk             |
|                                                             |
| 3. Changelog Extraction:                                    |
|    Reads release body text                                  |
+-------------------------------------------------------------+
         |
         v
[Prompt In-App Dialog] -> [Download Progress] -> [Launch Installer]
```

### Version Matching Rules
1. The tag name can be formatted as either `v1.0.1` or `1.0.1`. Sola automatically strips the leading `v`.
2. Sola compares versions numerically: `1.0.1` is detected as newer than `1.0.0`.
3. The release must have at least one asset ending with `.apk`.

---

## 2. GitHub Release Fields: What to Enter

When creating a new release on GitHub via the web interface (`Releases` -> `Draft a new release`), fill in the fields as follows:

| Field on GitHub | What to type | Purpose & Requirement |
|:---|:---|:---|
| **Choose a tag** | `v1.0.1` | **Crucial:** Sola compares this number against `BuildConfig.VERSION_NAME` (`1.0.0`). The tag must be a higher number than the currently installed build. Always use `v` followed by numbers and dots (e.g., `v1.0.1`, `v1.0.2`, `v1.1.0`). *(Select "+ Create new tag: v1.0.1 on publish" in the dropdown)* |
| **Target branch** | `main` | Leave as default (`main`). |
| **Release title** | `Sola v1.0.1` | Human-readable title. Sola does not depend on this for version math, so you can add details (e.g. `Sola v1.0.1 - OTA & Definitions Sync`). |
| **Description** | Bullet points of changes | **Displayed in-app:** Sola renders this markdown directly inside the app's update dialog as the changelog. |
| **Attach binaries** | Drop your `.apk` file | Sola scans the release assets and downloads the first file ending with `.apk`. |

### Example Release Description (What users see in Sola)

```markdown
### What's New in v1.0.1:
- Added in-app OTA update downloader
- Improved BMS cell balancing diagnostics
- Diagnostic definitions cloud sync
- Fixed MoveOS service mode switching timeout
```

### What Sola Does Behind the Scenes
1. The installed app reports its version: `versionName = "1.0.0"`.
2. Sola reads the GitHub API tag `v1.0.1`, strips the leading `v`, and extracts `1.0.1`.
3. Sola runs the semver check: `1.0.1 > 1.0.0` -> **True**.
4. It extracts the download URL from the `.apk` asset and presents the prompt:
   ```
   New Update Available: v1.0.1
   Installed version: v1.0.0

   [Changelog shown here]

   [Later]  [Download & Install]
   ```

---

## 3. Publishing an Update (Method A: Automated GitHub Actions)

The repository includes a GitHub Actions CI/CD workflow at `.github/workflows/release.yml`. When you push a tag starting with `v`, GitHub automatically compiles the release APK and creates the release with the binary attached.

### Step 1: Bump Version in Gradle
Open `app/build.gradle.kts` and increment `versionCode` and `versionName`:

```kotlin
defaultConfig {
    applicationId = "dev.sola"
    minSdk = 26
    targetSdk = 34
    versionCode = 2          // Increment integer
    versionName = "1.0.1"    // Match your new release tag
}
```

### Step 2: Commit and Push the Tag
Run the following in your terminal:

```bash
git add app/build.gradle.kts
git commit -m "Bump version to v1.0.1"
git push origin main

# Create and push the release tag
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions will start automatically, compile the APK, and create the GitHub Release with the attached APK.

---

## 4. Publishing an Update (Method B: Manual Web UI)

If you prefer building locally on your machine and uploading manually:

### Step 1: Compile the Release APK
Run the following Gradle command in the project root:

```powershell
.\gradlew.bat assembleStandardRelease
```

The compiled APK will be generated at:
```
app/build/outputs/apk/standard/release/app-standard-release.apk
```

### Step 2: Create the GitHub Release
1. Navigate to: `https://github.com/sillypari/Sola/releases`
2. Click **Draft a new release**.
3. In **Choose a tag**, type `v1.0.1` and click **Create new tag on publish**.
4. In **Release title**, type `Sola v1.0.1`.
5. In the description box, write your changelog.
6. Under **Attach binaries by dropping them here**, drag and drop:
   `app/build/outputs/apk/standard/release/app-standard-release.apk`
   *(You can rename it to `sola-v1.0.1-release.apk` for clarity)*
7. Click **Publish release**.

---

## 5. In-App Verification

1. Open Sola on your device.
2. Go to the **Developer** tab.
3. Scroll to the **Software Updates** card (featuring the download package vector icon).
4. Tap **Check for Updates**.
5. The app will detect the new release, display the changelog, and offer an **Update Now** button.

---

## 6. Important Gotchas
- **Do not mark as Draft**: Sola queries `/releases/latest`. Draft releases are hidden from the public API and will not be detected.
- **Pre-releases**: If you check "Set as a pre-release", GitHub excludes it from `/releases/latest`. Ensure it is marked as a standard release.
- **File extension**: The attached binary must have the `.apk` file extension.

---

## 7. Telemetry & Multi-Vehicle Tracking Schema

Sola records telemetry automatically on app startup, on foreground reopen, and on successful vehicle connection.

### How Multiple Vehicles per User are Recorded
When a single user connects to multiple scooters (e.g. testing an S1 Pro and an S1 Air):
- Sola pairs the user's persistent `client_id` with the scooter's unique `VIN` (`client_id + "::" + vin`).
- Scooter 1 gets its own row in Google Sheets with its odometer reading, firmware, and faults.
- When the same user connects to Scooter 2, a **new row** is created for Scooter 2 instead of overwriting Scooter 1.
- Reconnecting to Scooter 1 finds its existing row, updates `Last Seen` and `Odometer`, and increments `Total Pings`.

### Complete Google Apps Script (15 Columns)

```javascript
function doPost(e) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  
  if (sheet.getLastRow() === 0) {
    sheet.appendRow([
      "Last Seen", "First Seen", "Client ID", "City", "State", 
      "Country", "Vehicle Model", "VIN", "Odometer (km)", "Active Faults",
      "BMS Firmware", "MoveOS", "App Version", "Device Model", "Total Pings"
    ]);
    sheet.getRange(1, 1, 1, 15).setFontWeight("bold");
  }

  var data = {};
  try {
    data = JSON.parse(e.postData.contents);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }

  var clientId = data.client_id || "unknown";
  var now = Utilities.formatDate(new Date(), "Asia/Kolkata", "yyyy-MM-dd HH:mm:ss");
  var vehicle = data.vehicle || {};
  var variant = vehicle.variant || "None";
  var vin = vehicle.vin || "";
  var vehicleKey = vin ? vin : (variant !== "None" ? variant : "None");

  var values = sheet.getDataRange().getValues();
  var rowIndex = -1;
  var fallbackNoneIndex = -1;

  for (var i = 1; i < values.length; i++) {
    if (values[i][2] === clientId) {
      var rowVin = String(values[i][7] || "");
      var rowModel = String(values[i][6] || "");
      
      // Match by VIN if available, or by model variant
      if (vin && rowVin === vin) {
        rowIndex = i + 1;
        break;
      } else if (!vin && variant !== "None" && rowModel === variant) {
        rowIndex = i + 1;
        break;
      }
      
      if (rowModel === "None" || rowModel === "") {
        fallbackNoneIndex = i + 1;
      }
    }
  }

  // Upgrade empty "None" initial entry once scooter connects
  if (rowIndex === -1 && fallbackNoneIndex !== -1 && vehicleKey !== "None") {
    rowIndex = fallbackNoneIndex;
  }

  if (rowIndex > 0) {
    var currentPings = values[rowIndex - 1][14] || 1;
    sheet.getRange(rowIndex, 1).setValue(now);
    sheet.getRange(rowIndex, 4).setValue(data.city || "Unknown");
    sheet.getRange(rowIndex, 5).setValue(data.region || "Unknown");
    sheet.getRange(rowIndex, 6).setValue(data.country || "Unknown");
    if (variant !== "None") sheet.getRange(rowIndex, 7).setValue(variant);
    if (vin) sheet.getRange(rowIndex, 8).setValue(vin);
    if (vehicle.odometer_km !== undefined) sheet.getRange(rowIndex, 9).setValue(vehicle.odometer_km);
    if (vehicle.active_faults) sheet.getRange(rowIndex, 10).setValue(vehicle.active_faults);
    if (vehicle.bms_firmware) sheet.getRange(rowIndex, 11).setValue(vehicle.bms_firmware);
    if (vehicle.moveos_version) sheet.getRange(rowIndex, 12).setValue(vehicle.moveos_version);
    sheet.getRange(rowIndex, 13).setValue(data.app_version || "Unknown");
    sheet.getRange(rowIndex, 14).setValue(data.device_model || "Unknown");
    sheet.getRange(rowIndex, 15).setValue(currentPings + 1);
  } else {
    // New user or different vehicle connected by existing user
    sheet.appendRow([
      now,                                      // Last Seen
      now,                                      // First Seen
      clientId,                                 // Client ID
      data.city || "Unknown",                   // City
      data.region || "Unknown",                 // State / Region
      data.country || "Unknown",                // Country
      variant,                                  // Vehicle Model
      vin,                                      // VIN
      vehicle.odometer_km !== undefined ? vehicle.odometer_km : "N/A", // Odometer
      vehicle.active_faults || "None",          // Active Faults
      vehicle.bms_firmware || "None",           // BMS Firmware
      vehicle.moveos_version || "None",         // MoveOS
      data.app_version || "Unknown",            // App Version
      data.device_model || "Unknown",           // Device Model
      1                                         // Total Pings
    ]);
  }

  return ContentService.createTextOutput(JSON.stringify({ status: "success" }))
    .setMimeType(ContentService.MimeType.JSON);
}
