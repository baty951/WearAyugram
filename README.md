# WearAyugram

Автономный Telegram-клиент для WearOS с фичами Ayugram. Работает напрямую с Telegram API через TDLib — телефон не нужен.

<p align="center">
  <em>WearOS 3+ · TDLib · Jetpack Compose Material3 · arm64-v8a / armeabi-v7a</em>
</p>

## Возможности

- **Авторизация** — по номеру телефона (RemoteInput) или QR-коду, поддержка 2FA
- **MTProxy** — настройка прокси до авторизации; при старте прокси включается автоматически и отключается, если не подключился за 5 секунд (fallback на прямое соединение)
- **Чаты** — список с аватарами и бейджами непрочитанных, папки чатов (чипы под заголовком), галочки прочтения ✓/✓✓ у исходящих
- **Форумы** — супергруппы с темами: список тем (закреп, непрочитанные), отправка в конкретную тему
- **Поиск** — по своим чатам, публичным каналам/группам и глобально по сообщениям
- **Сообщения**:
  - текст — отправка через RemoteInput (клавиатура/голос)
  - голосовые — запись (MediaRecorder, OGG/OPUS) и воспроизведение с прогрессом
  - фото — превью в чате, полноэкранный просмотр с зумом (rotary + double-tap)
  - видео, кружочки и GIF — превью с кадром, полноэкранный плеер (VideoView), GIF зациклены
  - стикеры — статичные WEBP полностью, анимированные — статичным кадром
  - документы — имя/размер; картинки и видео файлами открываются во встроенных просмотрщиках
  - реакции — видны на пузырях (своя подсвечена), долгое нажатие открывает меню действий для отправки/снятия
  - реплаи — цитата оригинала над ответом; ответить можно из меню действий (долгое нажатие)
- **Ayugram-фичи**:
  - 👻 **Ghost Mode** — не отправлять статус «прочитано»
  - 🗑 **Anti-revoke** — удалённые собеседником сообщения сохраняются локально (Room) и помечаются в чате
  - ✎ **История редактирований** — старые версии сообщений сохраняются при правке; тап по сообщению с ✎ показывает их
  - 🔔 **Delete Notifications** — уведомление, когда собеседник удаляет сообщение
  - ⭐ **Local Premium** — локальный флаг Premium
- **Деградация под слабое железо** — автозагрузка фото выключена по умолчанию на low-RAM часах (фото по тапу), полноэкранный размер ограничен; очистка кэша медиа из настроек
- **Фоновая работа** — ForegroundService с постоянным TDLib-соединением, FCM-пуши для пробуждения процесса
- **Tile** — плитка WearOS с количеством непрочитанных

Сравнение с AyuGram для Android и Desktop — в [COMPARISON.md](COMPARISON.md).

## Сборка

### 1. Секреты

Скопируй `secrets.properties.example` в `secrets.properties` и заполни:

```properties
TG_API_ID=...        # с https://my.telegram.org
TG_API_HASH=...
RELEASE_STORE_FILE=../wearayugram-release.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Keystore для release-подписи (если нет):

```powershell
keytool -genkeypair -keystore wearayugram-release.keystore -alias wearayugram -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Firebase

Положи свой `google-services.json` (Firebase-проект с пакетом `su.kirian.wearayugram`) в `app/`. Без него FCM-пуши работать не будут, но приложение соберётся.

### 3. TDLib-бинарники

В репозитории уже лежат собранные `app/libs/td-android.jar` и `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libtdjni.so`.

Пересборка (нужен Docker Desktop):

```powershell
.\build-tdlib.ps1        # arm64-v8a (~40 мин при первом запуске)
.\build-tdlib-arm32.ps1  # armeabi-v7a (для 32-битных часов, напр. Samsung)
```

### 4. Сборка и установка

```powershell
.\gradlew assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

> Архитектуру часов можно узнать так: `adb shell getprop ro.product.cpu.abi`

## Настройка MTProxy

В UI: экран авторизации → кнопка **MTProxy** → ввести хост, порт, секрет → **Применить**.

Через adb (для отладки):

```powershell
adb shell am broadcast -a "su.kirian.wearayugram.SET_PROXY" -p "su.kirian.wearayugram" `
  --es host "proxy.example.com" --ei port 443 --es secret "ee..."
```

## Архитектура

```
app/src/main/java/su/kirian/wearayugram/
├── ayugram/        # Ghost Mode, Anti-revoke, Delete Notify, Local Premium, ProxyManager, настройки (DataStore)
├── data/
│   ├── local/      # Room: сохранённые удалённые сообщения
│   ├── repository/ # Auth/Chat/Message — реализации поверх TDLib
│   ├── service/    # ForegroundService (dataSync), FCM-сервис
│   └── tdlib/      # TelegramClient (JNI-обёртка, Flow апдейтов), мапперы
├── domain/         # Модели и интерфейсы репозиториев
└── presentation/   # Compose-экраны: auth, chatlist, chat (+фото/видео-просмотрщики),
                    # topics, search, settings, proxy, tile
```

Ключевые решения (подробности в комментариях кода):

- `TelegramClient` раздаёт апдейты TDLib через горячий `SharedFlow`; `authState` — отдельный `StateFlow`, чтобы не терять `Ready` для уже авторизованного пользователя
- История чата: `OpenChat` + повторные `GetChatHistory` (TDLib отдаёт только локальный кэш на первый запрос)
- `UpdateDeleteMessages` обрабатывается только при `isPermanent && !fromCache` (иначе выгрузка кэша помечает живые сообщения удалёнными)
- Пузырь сообщения — один `Text` с `AnnotatedString`: raw-контейнеры схлопываются внутри `TransformingLazyColumn`
- Обновления списка чатов батчатся (окно 400мс), `UpdateChatPosition` задросселирован
- Медиа: `DownloadFile(synchronous=true)` — suspend-вызов сам возвращает готовый путь; дедупликация по fileId, максимум 2 параллельных загрузки
- Декодирование картинок: `inSampleSize` до целевой ширины + `RGB_565` (стикеры — `ARGB_8888` ради прозрачности) + `LruCache` — обязательно для 32-битного heap часов
- Потоки сообщений ключуются парой (chatId, topicId) — темы форумов изолированы друг от друга
- Сборка TDLib использует новое topic-API (`MessageTopicForum`), структуру классов сверять через `javap` по `td-android.jar`

## Известные ограничения

- FCM-токен может не выдаваться при DPI-фильтрации GMS-трафика провайдером (`SERVICE_NOT_AVAILABLE`) — регистрация ретраится при каждом запуске; уведомления при живом ForegroundService работают и без FCM
- Отправка фото/видео/файлов с часов не реализована (камеры обычно нет)
- Анимированные стикеры (TGS/WEBM) показываются статичным кадром
- Пересылка сообщений не реализована; кастомные эмодзи-реакции не отображаются
- История редактирований сохраняет только правки, случившиеся пока приложение держало сообщение (TDLib не хранит старые версии)
- Звонки, истории, секретные чаты — вне скоупа форм-фактора
- AmbientMode не поддерживается

## Лицензия

Личный проект. TDLib — [Boost Software License 1.0](https://github.com/tdlib/td/blob/master/LICENSE_1_0.txt).
