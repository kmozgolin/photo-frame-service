# EasyFrame — Play Console: все ответы для анкеты

Шпаргалка для заполнения Google Play Console. Копируй значения по разделам.
Везде, где «No / None», ставим отрицательный вариант — приложение офлайн, без данных, без рекламы.

---

## 0. Create app (уже сделано)
| Поле | Значение |
|---|---|
| App name | `EasyFrame` |
| Default language | English (United States) – `en-US` |
| App or game | App |
| Free or paid | Free |
| Package name (из AAB) | `com.easycode.easyframe` |
| Declarations | Developer Program Policies — ✓, US export laws — ✓ |

---

## 1. Store listing → Main store listing

**App name (≤30):**
```
EasyFrame
```

**Short description (≤80):**
```
Simple, clean passe-partout borders for your photos: color, width, opacity.
```

**Full description (≤4000):**
```
EasyFrame is a simple photo framing tool focused on clean passe-partout borders — even, minimal mats around your photo, like a gallery mount. No ornate patterns or decorative shapes. Add a clean border and save the result to your gallery in just a few taps.

FEATURES
• Smart AI color — the app analyzes your shot and picks a frame color that highlights the key subject in the photo.
• One-touch thickness — control the side and top/bottom margins on an easy 2D pad. A magnetic guide helps you make an even frame on all sides.
• Eyedropper — grab any color straight from the image.
• Palette & presets — white, black, gray, warm, or your own color via the color wheel.
• Frame opacity — from fully solid to subtle.
• Rotate & zoom — straighten and inspect before saving.
• Correct orientation — portrait photos are never sideways.
• Save to gallery — PNG (with transparency) or JPEG.

PRIVACY
• Works fully offline.
• Collects no data, no internet access.
• No ads, no accounts, no tracking.

Free. If the app helps you, you can support the developer.
```

**Graphics:**
| Asset | File | Spec |
|---|---|---|
| App icon | `store/icon-512.png` | 512×512 PNG |
| Feature graphic | `store/feature-graphic-1024x500.png` | 1024×500 |
| Phone screenshots | (готовим, ≥2) | 16:9/9:16, ≤2:1, сторона 320–3840px |

---

## 2. Store settings
| Поле | Значение |
|---|---|
| App category | Photography |
| Tags | photo, frame, border, passe-partout (по желанию) |
| Store listing contact — email | `kmozgolin@gmail.com` |
| Store listing contact — phone | (по желанию, можно пусто) |
| Store listing contact — website | `https://github.com/kmozgolin/photo-frame-service` (по желанию) |
| External marketing | Off (нет рекламных кампаний) |

---

## 3. App content (декларации, левое меню → Policy → App content)

### Privacy policy
```
https://kmozgolin.github.io/photo-frame-service/privacy-policy.html
```

### App access
- **All functionality is available without special access** (нет логина/регистрации).

### Ads
- **No, my app does not contain ads.**

### Content rating
- Email: `kmozgolin@gmail.com`
- Category: **Utility, Productivity, Communication, or Other**
- Все вопросы анкеты → **No / None** (см. раздел 5 ниже).
- Ожидаемый рейтинг: **Everyone / PEGI 3**.

### Target audience and content
- Target age groups: **18 and over** (можно добавить 13–17). **НЕ отмечать «Under 13»** — иначе включится политика для детей.
- Are children a target audience? → **No**
- Appeal to children (unintentionally)? → **No**

### Data safety
- См. раздел 4 ниже — **No data collected / No data shared**.

### Прочие декларации (все отрицательные)
| Раздел | Ответ |
|---|---|
| Government apps | No |
| Financial features | None of these / My app doesn't provide any financial features |
| Health | No (Health apps declarations не применяются) |
| News app | No, my app is not a news app |
| COVID-19 contact tracing/status | No |
| Data safety — designed for families | No |

---

## 4. Data safety (подробно)

| Вопрос | Ответ |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data encrypted in transit? | N/A (данные не передаются) |
| Do you provide a way for users to request that their data is deleted? | N/A (данных нет) |

Итог в карточке: **No data collected · No data shared**.
Обоснование: приложение работает офлайн, нет разрешения INTERNET, не использует
аккаунты/аналитику/рекламу. Единственное разрешение — `WRITE_EXTERNAL_STORAGE`
(только Android ≤ 8.0) для сохранения готового изображения в галерею устройства.

---

## 5. Content rating — анкета (IARC), ответы по пунктам

Категория: **Utility, Productivity, Communication, or Other**

| Вопрос | Ответ |
|---|---|
| Violence | No |
| Sexuality / nudity | No |
| Language (profanity) | No |
| Controlled substances (drugs/alcohol/tobacco) | No |
| Gambling / simulated gambling | No |
| User-generated content / sharing | No |
| Shares user’s location | No |
| Allows purchases of digital goods | No |
| Promotes/sells regulated goods | No |
| Miscellaneous (other sensitive) | No |

→ Результат: **Everyone (ESRB) / PEGI 3 / USK 0**.

---

## 6. Технические факты приложения (если спросят)
| Поле | Значение |
|---|---|
| Package / applicationId | `com.easycode.easyframe` |
| versionCode / versionName | `3` / `1.2` |
| minSdk / targetSdk | 21 / 35 |
| Permissions | `WRITE_EXTERNAL_STORAGE` (maxSdkVersion=28) только |
| Internet | Нет (полностью офлайн) |
| In-app purchases | No |
| Ads | No |
| Signing | Play App Signing (принять при первом релизе) |
| AAB | `android/app/build/outputs/bundle/release/app-release.aab` |

---

## 7. Маршрут публикации (напоминание)
Аккаунт Personal → обязателен **Closed testing**:
1. Closed testing → создать релиз → залить AAB
2. Набрать **≥12 тестировщиков** (opt-in)
3. Тест идёт **≥14 дней**
4. «Apply for production» разблокируется → подать заявку → ревью

---

## 8. Release notes (для трека) — пример
**EN (`en-US`):**
```
First release of EasyFrame.
• Clean passe-partout borders for your photos
• Smart AI color, eyedropper, presets, color wheel
• Adjustable thickness and opacity
• Save to gallery as PNG or JPEG
• Works fully offline, no ads, no tracking
```
