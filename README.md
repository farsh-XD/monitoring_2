# HG6245N Monitor & Control (Android 7+ / API 24+)

Aplikasi live monitoring dan kontrol router/ONT FiberHome HG6245N (`http://192.168.1.100`) untuk Android 7.0 (API 24) ke atas:
- **Login & Autentikasi Router**: Input `username` dan `password`, mendukung *HTTP Basic Auth*, *form login* umum router/ONT, dan *session cookie* persisten.
- **100% Live Metrics**: `CPU Usage`, `Memory Usage`, `Suhu CPU` (`°C`), dan `Uptime` diambil langsung dari respons HTTP router (tanpa nilai seed statis saat terhubung).
- **Live Connected Users**: Daftar perangkat tersambung (`Hostname / Nama`, `IP Address`, `MAC Address`, `Interface`) di-parse langsung dari data DHCP / ARP / WLAN router.
- **Kontrol Router Aktif**:
  - Uji ping / *probe* ke IP target di jaringan lokal
  - Pengaturan SSID Wi-Fi & Password Wi-Fi
  - Reboot router langsung dari aplikasi
