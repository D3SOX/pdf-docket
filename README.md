# PDF Wallet

An Android app that stores your PDF tickets, passes and certificates and shows their barcodes in fullscreen.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://d3sox.me/pdfwallet-android/)
[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/D3SOX/pdfwallet-android)

The F-Droid badge opens this project's own F-Droid repository, which you add to your F-Droid client. Every commit to `main` publishes a new release there and on [GitHub Releases](https://github.com/D3SOX/pdfwallet-android/releases).

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="200" alt="Document list"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="200" alt="Barcode in fullscreen"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="200" alt="Documents filtered by tag"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="200" alt="Dark mode">

## Why this fork

PDF Wallet was open source until its author, Michael Troger, archived [the original repository](https://github.com/michaeltroger/pdfwallet-android) and continued the app as the proprietary PDFWallet Vault. Its paid "Pro" features moved behind an in-app purchase.

This fork keeps the app open source under the GPLv3. It rebuilds the interface with Jetpack Compose and Material 3, modeled on the new app, and includes the former Pro features for free: tags, tag filters, inverted PDF colors in dark mode, a forced light or dark theme and half-size barcodes.

## Features

- Import PDFs, or open them with PDF Wallet from a file browser, email client or web browser.
- Detect QR, Aztec, DataMatrix, PDF417, UPC-A, UPC-E, EAN-8, EAN-13, Code 39, Code 93, Code 128, ITF and Codabar barcodes and show them in fullscreen.
- Swipe between documents and switch between the barcode and the PDF.
- Tag, search and sort documents by import date, name or file size.
- Open password protected PDFs and lock the app with biometrics or the device PIN.
- Work offline, with no internet permission, ads or tracking.

## Building

The app requires JDK 21 to build and runs on Android 7.0 or later.

```sh
./gradlew assembleDebug
./gradlew detekt lintDebug testDebugUnitTest
```

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
