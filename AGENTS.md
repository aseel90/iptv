# Agent note — Zoryvo external updates

Read this before publishing an Android build.

- All **APK/external updates** for Selyro TV are to be distributed through `aseel90/zoryvo-app-hub`.
- An update that installs/replaces an APK is external even if Selyro initiates the download itself.
- The existing `update/latest.json` APK updater path is therefore a **legacy external-update channel**. Do not advance it for new releases unless the user explicitly asks to keep a second APK channel.
- Genuine in-app/internal data or configuration updates that do not replace the APK may remain inside Selyro.
- Preserve `com.selyro.tv`, increment `versionCode`, and preserve the existing signing certificate.
- Do not make this repository private unless explicitly requested.
- Before handing a new APK to Zoryvo, run the existing Android/TV verification and confirm signer continuity.
