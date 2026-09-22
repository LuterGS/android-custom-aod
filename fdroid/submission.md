New app: SG AOD

SG AOD is a custom always-on display for Android 12 and newer, showing a clock,
battery, notifications and music status. The app interface is currently Korean.
It uses Android platform APIs, has no Internet permission, and includes no ads,
analytics or proprietary SDKs. It does not require root or Samsung SDKs.
Device-specific background restrictions and sensor support affect its behavior.

- Source: https://github.com/LuterGS/android-custom-aod
- License: MIT
- Application ID: `dev.lutergs.sgaod`
- Initial version: `2.1.1` (`6`)
- Release: https://github.com/LuterGS/android-custom-aod/releases/tag/v2.1.1
- Issue tracker: https://github.com/LuterGS/android-custom-aod/issues
- Author: LuterGS; inclusion requested by the app owner.

The upstream repository contains English and Korean Fastlane descriptions,
icons and changelogs. Old v1 screenshots are deliberately omitted. The store
description documents the limitations of a normal Android activity used as an
AOD, including power consumption, panel refresh rate and burn-in mitigation.

The recipe enables reproducible builds using the upstream signed GitHub APK.
The expected signing certificate SHA-256 is:

```
2756a099ee43d26cd81ad2db79e2d4aa00577fcd2e444519afd3911f105eccdd
```

Version 2.1.1 disables AGP dependency metadata in APK/AAB packages. The build
uses AGP 9.3.0, Gradle 9.5.0, SDK 37 and JDK 17. Gradle daemon provisioning is
pinned to Amazon Corretto in `gradle/gradle-daemon-jvm.properties`.
The recipe removes the Foojay resolver plugin before scanning; daemon JDK
provisioning uses the already recorded download URLs independently of that plugin.
Automatic updates accept stable `vX.Y` / `vX.Y.Z` tags and exclude beta tags.

Validation and remaining review:

- Local Debug/Release builds, unit tests and Android Lint passed.
- Fastlane text lengths and icon dimensions checked.
- F-Droid metadata parsing, formatting and lint checked against fdroiddata's categories.
- Stable-tag update detection passed for v2.1.1.
- Both locales passed fdroiddata's `tools/check-fastlane.py` without warnings.
- F-Droid source scanning passed after removing the Foojay resolver plugin.
- The published v2.1.1 APK passed F-Droid binary scanning and signature verification.
- [F-Droid build verification on GitHub x86_64](https://github.com/LuterGS/android-custom-aod/actions/runs/35691033043)
  passed: the source-built APK matched the published APK and its signer matched
  `AllowedAPKSigningKeys`. This is not an official F-Droid CI run.
- Official F-Droid CI and maintainer review are required before publication.

When opening the MR, retain and complete the current **App inclusion** template
from fdroiddata, using the actual pipeline results. Submit only
`metadata/dev.lutergs.sgaod.yml` to fdroiddata; store listings live upstream.
