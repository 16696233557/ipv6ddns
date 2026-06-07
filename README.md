# IPv6DDNS

A minimal Android IPv6 DDNS client for the boss.
Detects the phone's public IPv6 address and pushes it to an Aliyun DNS AAAA record on a schedule.

## Features
- Periodic public IPv6 detection via 3 external echo services (api64.ipify.org / v6.ident.me / 6.ipw.cn)
- Aliyun DNS (Alidns) update via official SDK: AAAA record create / update / no-op
- Foreground service + persistent notification (Android 8+ compliant)
- WorkManager fallback (15 min minimum) in case the service is killed
- Boot auto-start (BOOT_COMPLETED / MY_PACKAGE_REPLACED)
- EncryptedSharedPreferences for the AccessKey Secret
- Chinese + English UI (Material 3, Compose)

## Requirements
- Android Studio Hedgehog (2023.1) or newer
- JDK 17
- minSdk 24, targetSdk 34

## Open & Build
1. Open Android Studio → File → Open → select the `IPv6DDNS` folder.
2. Let it Gradle Sync (it will download `gradle-wrapper.jar` distribution and dependencies).
3. Plug in a phone with USB debugging on (or start an emulator), click ▶ Run.

> If you ever see "wrapper not found" error after a fresh checkout, run
> `gradle wrapper --gradle-version 8.5` in the project root.

## Configure the app
1. Create an Aliyun sub-account with the policy below (least privilege).
2. In the app, open the **Config** tab and fill in:
   - AccessKey ID
   - AccessKey Secret
   - Root domain (e.g. `example.com`)
   - Subdomain (e.g. `home.example.com` — or `@` for the apex)
   - Detection interval (default 30 min)
3. Open the **Status** tab → tap **Start service**.
4. Android will pop a battery-whitelist dialog. Accept it.

## Aliyun RAM policy (least privilege)
Create a custom policy in the RAM console with this JSON:

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "alidns:DescribeDomainRecords",
        "alidns:DescribeDomains"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "alidns:AddDomainRecord",
        "alidns:UpdateDomainRecord",
        "alidns:DeleteDomainRecord"
      ],
      "Resource": "acs:alidns:*:acs:domain/your-domain.com"
    }
  ]
}
```

Replace `your-domain.com` with your actual root domain. Attach this policy to a
new RAM user, then create an AccessKey for that user and paste the keys into the app.

## Known limitations
- Android 8+ aggressively kills background services. The foreground service +
  battery whitelist is the best we can do without root.
- On MIUI / EMUI / ColorOS, the user must additionally enable **Autostart** in
  the system settings; the app's boot receiver alone is not enough.
- WorkManager minimum period is 15 minutes; the in-service loop honors the user
  setting (1/5/10/30/60 min) without the OS-imposed floor.
- AccessKey is stored in EncryptedSharedPreferences, which uses the Android
  Keystore. Rooted devices are still vulnerable — use a dedicated least-privilege
  sub-account.

## Project layout
```
app/src/main/java/com/boss/ipv6ddns/
├── Ipv6DdnsApp.kt            Application class + notif channel + WorkManager Configuration.Provider
├── MainActivity.kt           Entry activity, hosts Compose, requests permissions
├── data/
│   ├── DDNSConfig.kt         Plain data class
│   ├── ConfigRepository.kt   EncryptedSharedPreferences + plain SharedPreferences
│   └── LogRepository.kt      In-memory ring buffer (200 entries)
├── di/
│   └── AppContainer.kt       Hand-rolled DI singleton
├── network/
│   ├── OkHttpProvider.kt     Shared OkHttpClient
│   ├── Ipv6Probe.kt          3-source external IPv6 probe
│   └── AliyunDnsClient.kt    Alidns SDK wrapper
├── service/
│   ├── DDNSForegroundService.kt   Foreground service with notification
│   ├── DDNSScheduler.kt           Periodic loop
│   ├── DDNSState.kt               Shared StateFlow
│   └── BootReceiver.kt            BOOT_COMPLETED receiver
├── work/
│   └── DDNSWorker.kt              WorkManager fallback
└── ui/
    ├── MainScreen.kt              Bottom-nav scaffold
    ├── MainViewModel.kt           Bridges container → Compose
    ├── StatusScreen.kt
    ├── ConfigScreen.kt
    ├── LogScreen.kt
    └── theme/
```
