# 📱 eSMK Altan V.4 - Release App Mobile

Aplikasi Android resmi untuk sistem ujian SMK Altan.  
Dirancang untuk **Android versi 8+** dengan fitur keamanan berlapis.

---

## 🔒 Update Fitur Keamanan

| Fitur | Keterangan |
|-------|------------|
| **Kiosk Mode** | Mengunci perangkat, tidak bisa keluar ke launcher |
| **Blokir Screenshot** | `FLAG_SECURE` - tidak bisa screenshot/record |
| **Blokir Overlay** | Monitoring window overlay dari app lain, termasuk blokir AI dari sistem Android |
| **Blokir Tombol** | Back, Home, Recents, Volume diblokir |
| **Full Screen** | Status bar & navigation bar tersembunyi |
| **Blokir Split Screen** | `resizeableActivity=false` |
| **Foreground Service** | App tidak di-kill sistem Android |
| **Custom User-Agent** | Website dapat mendeteksi app resmi |
| **Blokir URL Eksternal** | Terbatas hanya untuk domain server ujian |
| **Disable Copy-Paste** | Script diinjeksi ke halaman web |
| **Device Admin** | Mode paling aman via ADB |

### Padiweb Labs Developer

---

## 🚀 PANDUAN SETUP LENGKAP

### TAHAP 1: Persiapan Akun & Tools

**1.1. Buat akun GitHub**
- Buka https://github.com
- Klik "Sign Up" → isi email, password, username
- Verifikasi email

**1.2. Install Android Studio**
- Download dari: https://developer.android.com/studio
- Install dengan semua komponen default
- Buka Android Studio → tunggu sampai selesai setup

**1.3. Install Git**
- Download dari: https://git-scm.com/downloads
- Install dengan pengaturan default
- Buka terminal/CMD, verifikasi: `git --version`

---

### TAHAP 2: Buat Repository di GitHub

1. Login ke GitHub
2. Klik tombol **"+"** di pojok kanan atas → **"New repository"**
3. Isi form:
   - **Repository name:** `ujian-smkaltan-android`
   - **Description:** `Aplikasi ujian kiosk untuk SMK Altan`
   - **Visibility:** Private ✅ (jangan public!)
   - Centang **"Add a README file"**
4. Klik **"Create repository"**

---

### TAHAP 3: Clone & Setup Project di Android Studio

**3.1. Clone repository**
```bash
# Buka terminal, arahkan ke folder kerja Anda
cd C:\Users\NamaAnda\Documents   # Windows
# atau
cd ~/Documents                    # Mac/Linux

# Clone repository (ganti USERNAME dengan username GitHub Anda)
git clone https://github.com/USERNAME/ujian-smkaltan-android.git

# Masuk ke folder
cd ujian-smkaltan-android
```

**3.2. Copy semua file project ke sini**
- Copy seluruh isi folder project ini ke dalam folder yang baru di-clone
- Pastikan struktur folder seperti di bawah

**3.3. Buka di Android Studio**
- Buka Android Studio
- File → Open → pilih folder `ujian-smkaltan-android`
- Tunggu Gradle sync selesai (bisa 5-10 menit pertama kali)

---

### TAHAP 4: Struktur Folder Project

```
ujian-smkaltan-android/
├── .github/
│   └── workflows/
│       └── build.yml          ← Auto build APK di GitHub
├── app/
│   ├── src/main/
│   │   ├── java/com/smkaltan/ujian/
│   │   │   ├── MainActivity.kt           ← UTAMA: WebView + Keamanan
│   │   │   ├── ExamDeviceAdminReceiver.kt ← Device Admin
│   │   │   └── SecurityService.kt        ← Foreground Service
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── xml/
│   │   │       ├── device_admin.xml
│   │   │       └── data_extraction_rules.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── .gitignore
└── README.md
```

---

### TAHAP 5: Build APK

**5.1. Build via Android Studio**
- Menu atas: **Build → Build Bundle(s)/APK(s) → Build APK(s)**
- Tunggu proses selesai
- Klik notifikasi "APK(s) generated" untuk melihat file APK

**5.2. Build via Terminal (lebih cepat)**
```bash
# Di folder project
./gradlew assembleDebug         # Untuk testing
./gradlew assembleRelease       # Untuk distribusi
```

APK berada di: `app/build/outputs/apk/debug/app-debug.apk`

---

### TAHAP 6: Upload ke GitHub

```bash
# Setelah semua file disiapkan di folder project
git add .
git commit -m "feat: initial release aplikasi ujian kiosk"
git push origin main
```

Setiap push ke GitHub akan otomatis build APK via **GitHub Actions**.  
Lihat hasilnya di tab **Actions** di repository GitHub Anda.

---

### TAHAP 7: Aktifkan Device Owner Mode (SANGAT DISARANKAN)

Mode ini memberikan keamanan **jauh lebih kuat** dari screen pinning biasa.

**Syarat:**
- HP harus **factory reset** dulu (atau belum ada akun Google)
- Sambungkan HP ke komputer via USB
- Aktifkan Developer Options di HP

**Cara aktifkan:**
```bash
# Install ADB (Android Debug Bridge)
# Download platform-tools dari: https://developer.android.com/tools/releases/platform-tools

# Sambungkan HP via USB, aktifkan USB Debugging di Developer Options
# Jalankan perintah ini di terminal:
adb shell dpm set-device-owner com.smkaltan.ujian/.ExamDeviceAdminReceiver
```

Output sukses:
```
Active admin set to component {com.smkaltan.ujian/com.smkaltan.ujian.ExamDeviceAdminReceiver}
Active admin is now the Device Owner
```

---

### TAHAP 8: Integrasi dengan Website Ujian

Tambahkan kode ini di website `ujiansmkaltan.sch.id` untuk deteksi app:

```javascript
// Cek apakah berjalan di app Android resmi
if (window.isNativeApp && window.AndroidBridge) {
    console.log('Ujian berjalan di aplikasi Android resmi');
    
    // Tampilkan tombol selesai ujian yang berbeda untuk app
    document.getElementById('btnSelesai').addEventListener('click', function() {
        // Panggil fungsi untuk keluar dari kiosk mode
        window.AndroidBridge.examFinished();
    });
}

// Blokir akses dari browser biasa (opsional)
const isAndroidApp = navigator.userAgent.includes('UjianSMKAltan-AndroidApp');
if (!isAndroidApp) {
    // Redirect atau tampilkan pesan error
    // document.body.innerHTML = '<h1>Gunakan aplikasi resmi untuk mengerjakan ujian</h1>';
}
```

---

## 📦 Install APK ke HP Siswa

1. Download APK dari GitHub Actions atau build lokal
2. Kirim ke HP siswa via USB / email / Google Drive
3. Di HP: **Pengaturan → Keamanan → Install dari sumber tidak dikenal → Izinkan**
4. Install APK
5. (Opsional) Jalankan Device Owner via ADB

---

## 🔧 Kustomisasi

Edit file `MainActivity.kt`, cari baris:
```kotlin
const val EXAM_URL = "https://www.ujiansmkaltan.sch.id"
```
Ganti dengan URL lain jika diperlukan.

---

## ❓ FAQ

**Q: Siswa bisa uninstall app-nya?**  
A: Tidak bisa jika Device Owner mode aktif. Tanpa Device Owner, bisa diblokir via MDM.

**Q: App bisa di-bypass pakai ADB?**  
A: Bisa, tapi memerlukan pengetahuan teknis tinggi. Solusi: nonaktifkan USB Debugging di HP siswa.

**Q: Bagaimana siswa keluar setelah selesai ujian?**  
A: Website memanggil `window.AndroidBridge.examFinished()` yang akan membuka kiosk mode dan keluar app.

---

## 📞 Kontak

Dibuat untuk **SMK Altan**  
Website ujian: https://www.ujiansmkaltan.sch.id
