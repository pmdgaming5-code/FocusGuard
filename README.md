# FocusGuard

Kotlin ve Jetpack Compose ile geliştirilen, cihazda yerel çalışan Android odak ve uygulama kullanım süresi yöneticisi.

## Proje özellikleri

- Android 12 ve üzeri (minSdk 31), Material 3 arayüz.
- Launcher üzerinden açılabilen uygulamaları listeleme.
- Her uygulama için günlük limit ve tek oturum limiti.
- UsageStatsManager olayları ile ön plandaki uygulamanın kullanımını takip etme.
- Süre dolduğunda kullanıcı izniyle tam ekran uygulama üstü uyarı.
- Özelleştirilebilir Türkçe odak mesajı.
- 4-12 rakamlı PIN; yeni PIN kaydetme/değiştirme sırasında mevcut PIN doğrulaması.
- PIN doğrulamasında PBKDF2-HMAC-SHA256 ve Android Keystore HMAC anahtarı; PIN düz metin olarak saklanmaz.
- Yanlış PIN denemelerinde artan bekleme süresi.
- Kilit açma seçenekleri: 5 dakika, 10 dakika, 15 dakika veya yalnızca mevcut uygulama oturumu.
- WorkManager ile saklanan süreli odak oturumu ve tamamlanma bildirimi.
- Room ile günlük kullanım/uygulama limitlerinin, DataStore ile ayarların saklanması.
- Açık/koyu tema, ana panel ve günlük kullanım istatistikleri.
- Kullanıcı daha önce etkinleştirdiyse yeniden başlatma sonrası izlemeyi tekrar başlatmayı deneme.
- Device Owner olarak yapılandırılmış özel test cihazları için Lock Task Mode entegrasyonu.
- Reklam, analiz SDK'sı veya internet izni yok.

## Gerçek Android sınırları

Normal bir Android uygulaması bütün işletim sistemini sınırsız biçimde kilitleyemez. Uygulama sınırlama özelliği Kullanım Erişimi, diğer uygulamaların üzerinde gösterme izni, izleme servisinin çalışması ve üreticinin pil yönetimi davranışına bağlıdır. Android veya kullanıcı servisi durdurabilir; izinler sonradan geri alınabilir. FocusGuard, engellemenin etkin olmadığını tespit ettiğinde bunu başarılıymış gibi göstermemelidir.

UsageStatsManager verileri Android tarafından olaylar halinde sunulur. Bu uygulama olayları yaklaşık saniyelik aralıklarla okuyarak kullanım süresi biriktirir; bu nedenle sayaç laboratuvar düzeyinde milisaniye hassasiyetinde bir kronometre değildir. Çoklu pencere, ekran kilidi, yoğun sistem yükü ve üreticiye özel arka plan kısıtlamaları ölçümü etkileyebilir.

Gerçek kiosk / Lock Task modu yalnızca cihazın önceden Device Owner olarak yönetilen özel bir cihaza dönüştürülmesiyle kullanılabilir. Bu sıradan bir izin değildir. **Günlük kullandığın kişisel telefonunu Device Owner/kiosk cihazına dönüştürme.** Kiosk özelliğini yalnızca test için ayrılmış ve kurtarma erişimi önceden planlanmış bir cihazda dene. PIN unutulursa FocusGuard'ın içinden çıkış mümkün olmayabilir; bu nedenle kurtarma yolunu bilmediğin bir cihazda kiosk modunu başlatma.

Android'in acil arama, sistem güvenlik ekranları ve kritik işletim sistemi işlevleri FocusGuard tarafından ele geçirilmez. Uygulama, Android ebeveyn denetimlerinin veya kurumsal cihaz yönetiminin güvenlik sınırı değildir. PIN, cihaz sahibini/root/ADB erişimini engelleyen bir sistem parolası değil, kişisel odak disiplini bariyeridir.

## İzinler

İlk kurulumda kullanıcı tarafından açıkça verilmelidir:

1. **Kullanım erişimi:** Ayarlar > Güvenlik/Gizlilik veya Özel uygulama erişimi > Kullanım erişimi > FocusGuard.
2. **Diğer uygulamaların üzerinde gösterme:** Ayarlar > Uygulamalar > Özel uygulama erişimi > Diğer uygulamaların üzerinde göster > FocusGuard.
3. **Bildirimler:** Android 13 ve üzeri sürümlerde izin verilmesi önerilir. Takip servisi görünür bir bildirim kullanır.
4. **Yeniden başlatma sonrası devam:** Android, üretici pil yönetimi ve servis başlatma politikalarına göre yeniden başlatma sonrası otomatik başlatmayı engelleyebilir. Başlamazsa FocusGuard'ı açıp takibi yeniden başlat.

İzin pencereleri uygulama tarafından kendiliğinden onaylanmaz; kullanıcı Android ayarlarında onay verir.

## Windows 11 ile derleme

Bu depo Gradle wrapper yapılandırma dosyasını içerir; wrapper çalıştırma betikleri ve wrapper JAR dosyası henüz eklenmemiştir. Yerel Gradle 9.6.0 kurulu olmalıdır veya Android Studio'da Gradle 9.6.0 yerel dağıtımı seçilmelidir.

1. Android Studio'yu aç ve JDK 17 ile Android SDK Platform 36'yı kur.
2. Depoyu klonla:

   `git clone https://github.com/pmdgaming5-code/FocusGuard.git`

3. Windows PowerShell'de proje klasörüne gir. Gradle 9.6.0 PATH üzerindeyse wrapper dosyalarını üret:

   `gradle wrapper --gradle-version 9.6.0`

4. Birim testlerini ve debug APK'yı derle:

   `./gradlew.bat testDebugUnitTest assembleDebug`

5. APK şu konumda oluşur:

   `app\build\outputs\apk\debug\app-debug.apk`

Android Studio menüsünden Build > Build Bundle(s) / APK(s) > Build APK(s) da kullanılabilir; wrapper dosyaları oluşturulmadan önce Android Studio'ya yerel Gradle 9.6.0 dağıtımını seçmen gerekir.

## İlk kullanım

1. FocusGuard'ı aç.
2. Ayarlar sayfasından Kullanım Erişimi ve Diğer uygulamaların üzerinde göster izinlerini ver.
3. 4-12 rakamlı PIN oluştur.
4. Uygulama Süreleri bölümünden izlemek istediğin uygulamaları ekle ve günlük/oturum limitlerini belirle.
5. Ana Sayfa'dan izlemeyi başlat.
6. Odak bölümünden 25, 45, 60, 90 dakika veya özel süreli bir oturum başlat.
7. Süre dolduğunda uyarıda PIN'i girip 5/10/15 dakika veya mevcut oturum için izin seç.

## Test ve doğrulama durumu

- Limit hesaplaması için JVM birim testleri eklendi: günlük limit sınırı, oturum limiti ve odak modunda seçili uygulamaları kontrol eder.
- GitHub Actions iş akışı `.github/workflows/android-ci.yml` her main push ve pull request'te birim testleri ile debug APK derlemesini çalıştırmak üzere ayarlandı.
- Bu kaynak güncellemesi sırasında fiziksel Android cihaz üzerinde test yapılmadı ve başarılı bir CI sonucu doğrulanmadı. Bu nedenle APK'nın derlendiği veya gerçek cihaz üzerinde bütün kilit akışlarının doğrulandığı iddia edilmiyor.

## Şu anki kapsamın dışında

- Biyometrik PIN açma seçeneği.
- Harf/rakam içeren metin parolası; bu sürüm yalnızca sayısal PIN kullanır.
- Gelecekteki belirli bir saat için ileri tarihli odak oturumu başlatma; odak oturumları başlat düğmesine basıldığında başlar.
- OEM'lerin her birinde kesintisiz servis garantisi.
