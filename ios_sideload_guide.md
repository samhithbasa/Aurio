# How to Build & Install Aurio on iOS with iLoader / Sideloading

This guide explains how to generate the iOS application package (**`Aurio.ipa`**) and install it directly onto an iPhone or iPad using **iLoader**, **Sideloadly**, or **AltStore**.

---

## 1. Prerequisites
* **macOS Machine / Mac Cloud Runner** (GitHub Actions / Codemagic / local Mac) with **Xcode 15+** installed.
* **iLoader** (or Sideloadly / AltStore) installed on your PC or Mac.
* An Apple ID (free personal Apple ID or Apple Developer account).
* An iPhone or iPad connected via USB or Wi-Fi.

---

## 2. Generating the `Aurio.ipa` File

### Step 2.1: Compile the Kotlin Multiplatform iOS Framework
From the project root in terminal:
```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
This compiles `SharedAurio.framework` containing all shared Kotlin domain logic, 8D/16D spatial DSP algorithms, and models.

### Step 2.2: Build the iOS Archive in Xcode
Open the `iosApp` project in Xcode, or run the command line build:
```bash
xcodebuild -workspace iosApp/iosApp.xcworkspace \
           -scheme iosApp \
           -configuration Release \
           -sdk iphoneos \
           -archivePath build/Aurio.xcarchive \
           archive
```

### Step 2.3: Export the `.ipa` Package
```bash
xcodebuild -exportArchive \
           -archivePath build/Aurio.xcarchive \
           -exportPath build/out \
           -exportOptionsPlist iosApp/ExportOptions.plist
```
The output file will be generated at:
`build/out/Aurio.ipa`

---

## 3. Installing via iLoader / Sideloadly / AltStore

### Using iLoader:
1. Connect your iPhone to your computer.
2. Launch **iLoader**.
3. Drag and drop `build/out/Aurio.ipa` into iLoader.
4. Enter your Apple ID credentials to sign the app.
5. Click **Install**.

### Using Sideloadly:
1. Open **Sideloadly**.
2. Connect your iPhone (ensure your device is trusted).
3. Drag and drop the `Aurio.ipa` file into the Sideloadly window.
4. Enter your Apple ID and click **Start**.

### Final Step on iPhone (First Time Only):
1. On your iPhone, open **Settings** → **General** → **VPN & Device Management**.
2. Tap your Apple ID under *Developer App*.
3. Tap **Trust [Your Apple ID]**.
4. Open **Aurio** from your iPhone home screen!
