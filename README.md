# Unutilmas Ta'm — Android WebView ilovasi

Kompyutersiz, faqat telefondan APK yasash uchun tayyor loyiha.
Yig'ish GitHub serverlarida (Actions) bajariladi.

---

## 1-qadam. GitHub'ga yuklash

1. GitHub'da yangi repozitoriya yarating, masalan `unutilmastam-apk`
2. Shu papkadagi hamma narsani yuklang (`Add file → Upload files`)
3. Tuzilma shunday bo'lishi kerak:

```
unutilmastam-apk/
├── .github/workflows/build-apk.yml
├── .github/workflows/create-keystore.yml
└── android-webview/
    ├── settings.gradle
    ├── build.gradle
    ├── gradle.properties
    └── app/
        ├── build.gradle
        └── src/main/...
```

> `.github` papkasi nuqta bilan boshlanadi — GitHub web'da papka yaratish uchun
> fayl nomiga to'liq yo'lni yozing: `.github/workflows/build-apk.yml`

---

## 2-qadam. Imzo kalitini yasash (FAQAT BIR MARTA)

1. Repozitoriyada **Actions** → **"Imzo kaliti yasash (bir marta)"** → **Run workflow**
2. Parol o'ylab toping (kamida 6 belgi) va yozing. **Bu parolni eslab qoling.**
3. Ishlab bo'lgach, pastdagi **IMZO-KALITI-SAQLANG** faylini yuklab oling
4. Ichidagi `keystore.base64.txt` faylni oching va **butun matnni** nusxalang

> ⚠️ `release.keystore` faylni Google Drive'ga ham saqlang.
> Yo'qotsangiz, ilovaning yangi versiyasini chiqara olmaysiz.

---

## 3-qadam. Secrets qo'shish

**Settings → Secrets and variables → Actions → New repository secret**

To'rtta maxfiy qiymat qo'shiladi:

| Nomi | Qiymati |
|------|---------|
| `KEYSTORE_BASE64` | `keystore.base64.txt` ichidagi uzun matn |
| `KEYSTORE_PASSWORD` | 2-qadamdagi parol |
| `KEY_PASSWORD` | o'sha parolning o'zi |
| `KEY_ALIAS` | `unutilmastam` |

---

## 4-qadam. APK yasash

1. **Actions** → **"APK yasash"** → **Run workflow**
2. 5–10 daqiqa kuting (birinchi safar sekinroq)
3. Tugagach pastdagi **UnutilmasTam-APK** ni yuklab oling

Ichida ikkita fayl bo'ladi:

- **`UnutilmasTam.apk`** — to'g'ridan-to'g'ri o'rnatish uchun (Telegram orqali tarqating)
- **`UnutilmasTam.aab`** — Google Play Store uchun

---

## Yangi versiya chiqarish

Saytdagi (`index.html`) o'zgarishlar **avtomatik** ko'rinadi — APK ni qayta yasash shart emas.

APK ni faqat quyidagi hollarda qayta yasang:
- ilova nomi yoki ikonkasi o'zgarsa
- Play Store'ga yangi versiya yuklasangiz

Bunda `android-webview/app/build.gradle` ichida:
```
versionCode 1   →   2
versionName "1.0" → "1.1"
```
o'zgartirib, yana **Run workflow** bosing.

---

## Nimalar sozlangan

| Imkoniyat | Holati |
|---|---|
| Kamera (QR skanerlash) | ✅ ruxsat so'raydi |
| Joylashuv (yetkazib berish) | ✅ ruxsat so'raydi |
| Mahsulot rasmini yuklash | ✅ fayl tanlash oynasi |
| Chekni PDF/rasm saqlash | ✅ maxsus ko'prik orqali |
| Telegram/telefon havolalari | ✅ tashqi ilovada ochiladi |
| "Orqaga" tugmasi | ✅ sayt ichida orqaga qaytadi |
| Offline ishlash | ✅ service worker orqali |
| Tortib yangilash | ✅ saytning o'z funksiyasi |

---

## Muhim eslatma

Chekni saqlash Android'da maxsus ko'prik orqali ishlaydi. Buning uchun
serverdagi `index.html` **v8.7 yoki undan yangi** bo'lishi shart.
Eski versiya bilan chek yuklab olinmaydi.
