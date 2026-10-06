# SpotifyCloneYT

Aplikasi pemutar musik ala Spotify untuk Android. Daftar lagu diambil dari **Cloud Firestore**,
file audio/gambar disimpan di **Cloud Storage for Firebase**, dan pemutaran berjalan di background
lewat **Jetpack Media3** (ExoPlayer + `MediaSessionService`) lengkap dengan notifikasi media.

## Fitur

- Daftar semua lagu dari Firestore (judul, artis, cover).
- Mini player di bawah layar: cover, judul yang bisa di-swipe untuk pindah lagu, tombol play/pause.
- Layar detail lagu: cover, seek bar, waktu berjalan/durasi, play/pause, previous/next
  (punya layout khusus landscape).
- Tetap memutar saat aplikasi di background, kontrol dari notifikasi, lock screen, dan Bluetooth.
- Pesan error yang jelas + tombol "Retry" (Firebase belum dikonfigurasi, akses ditolak, offline).

## Kompatibilitas

| | Versi |
|---|---|
| `minSdk` | 24 (Android 7.0) — minimum Navigation 2.10 (Media3, Firebase, Hilt butuh 23) |
| `targetSdk` / `compileSdk` | 37 (Android 17) — Google Play mewajibkan minimal API 36 sejak 31 Agustus 2026 |
| Diuji otomatis (CI, emulator) | API 31 (Android 12, 2021) dan API 37 (Android 17, 2026) |

Perubahan perilaku Android yang sudah ditangani:

- **Android 12**: `android:exported` eksplisit, `PendingIntent.FLAG_IMMUTABLE`, aturan memulai
  foreground service.
- **Android 13**: izin runtime `POST_NOTIFICATIONS` untuk notifikasi pemutaran.
- **Android 14**: foreground service bertipe `mediaPlayback` + izin
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- **Android 15/16**: edge-to-edge wajib — inset status bar, navigation bar, dan display cutout
  ditangani di `MainActivity`; predictive back didukung lewat Navigation Component.
- **Android 17**: background audio hardening (ditangani `MediaSessionService` Media3) dan
  pembatasan orientasi/resizability di layar besar (aplikasi tidak mengunci orientasi).
- Tidak ada native library di APK, jadi persyaratan 16 KB page size Google Play terpenuhi.

## Kebutuhan build

- Android Studio versi terbaru yang mendukung Android Gradle Plugin 9.4.
- JDK 17 atau lebih baru untuk menjalankan Gradle (JDK bawaan Android Studio sudah cukup).
- Toolchain: AGP 9.4.0, Gradle 9.8.0, Kotlin 2.4.20 (built-in Kotlin AGP 9), KSP 2.3.12,
  Hilt 2.60.1, Media3 1.11.1, Firebase BoM 34.16.0. Semua versi ada di
  [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

```bash
./gradlew assembleDebug            # build APK debug
./gradlew testDebugUnitTest        # unit test
./gradlew lintDebug                # lint
./gradlew connectedDebugAndroidTest  # instrumented test (butuh emulator/device)
```

## Setup Firebase (wajib agar daftar lagu muncul)

Tanpa konfigurasi Firebase, aplikasi tetap bisa di-build dan dijalankan, tetapi layar utama
menampilkan pesan *"Firebase is not configured"*.

1. Buat project di [Firebase console](https://console.firebase.google.com/).
2. Tambahkan aplikasi Android dengan package name `com.plcoding.spotifycloneyt`.
3. Download `google-services.json` dan letakkan di folder `app/` (`app/google-services.json`).
   Plugin Google Services hanya di-apply jika file ini ada.
4. Aktifkan **Cloud Firestore** dan buat collection `songs`. Setiap dokumen berisi:

   | Field | Tipe | Keterangan |
   |---|---|---|
   | `mediaId` | string/number | ID unik lagu. Jika kosong, ID dokumen yang dipakai. |
   | `title` | string | Judul lagu |
   | `subtitle` | string | Nama artis |
   | `songUrl` | string | URL audio (`https://...` atau `gs://bucket/path.mp3`). **Wajib.** |
   | `imageUrl` | string | URL cover (`https://...` atau `gs://...`) |

   Dokumen tanpa `songUrl` diabaikan. Gunakan URL `https` — koneksi `http` biasa diblokir Android.

5. Upload file audio dan cover ke **Cloud Storage**. Anda bisa menyimpan *download URL*
   (`https://firebasestorage.googleapis.com/...`) atau path `gs://` langsung di Firestore;
   path `gs://` dikonversi otomatis menjadi download URL oleh aplikasi.
6. Atur security rules agar aplikasi boleh membaca data (contoh: baca publik, tulis ditolak):

   ```
   // Firestore
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /songs/{songId} {
         allow read: if true;
         allow write: if false;
       }
     }
   }
   ```

   ```
   // Storage (hanya dibutuhkan jika memakai path gs://)
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /{allPaths=**} {
         allow read: if true;
         allow write: if false;
       }
     }
   }
   ```

## Struktur kode

```
app/src/main/java/com/plcoding/spotifycloneyt/
├── SpotifyApplication.kt         # @HiltAndroidApp, Timber
├── data/entities/Song.kt         # model + parsing dokumen Firestore
├── data/remote/MusicDatabase.kt  # baca Firestore, resolve URL gs:// lewat Storage
├── di/AppModule.kt               # Glide
├── exoplayer/
│   ├── MusicService.kt           # MediaSessionService + ExoPlayer (background playback)
│   ├── MusicServiceConnection.kt # MediaController untuk UI
│   └── MediaItemExt.kt           # konversi Song <-> MediaItem
├── adapters/                     # RecyclerView & ViewPager2 adapter
└── ui/                           # MainActivity, HomeFragment, SongFragment, ViewModel
```
