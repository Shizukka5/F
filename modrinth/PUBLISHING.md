# Как выложить ChunkSwap на Modrinth

## 1. Подготовка
1. Зарегистрируйтесь на modrinth.com (можно через GitHub).
2. Соберите jar на GitHub (вкладка Actions → chunkswap-mod). Нужен файл `chunkswap-1.1.0.jar`.
3. Протестируйте его в игре перед публикацией.

## 2. Создание проекта
Нажмите **+ → New project** (Создать проект) и заполните:

| Поле | Значение |
| --- | --- |
| Name | ChunkSwap |
| URL (slug) | chunkswap |
| Summary | Break a block and the whole chunk turns into it. 7 modes, random blocks, chance and an in-game settings menu. |
| Visibility | Public (сначала можно Draft) |

## 3. Страница проекта (вкладка Settings)
- **Description:** вставьте весь текст из файла `DESCRIPTION.md` (там английский и русский).
- **Icon:** загрузите `icon.png` (лежит рядом).
- **Categories:** Game mechanics, Cursed.
- **Environments:** Client — Optional, Server — Required.
- **License:** MIT.
- **Links → Source code:** ссылка на ваш GitHub-репозиторий (он должен быть Public).
- **Issue tracker:** ссылка на вкладку Issues в репозитории (по желанию).

## 4. Загрузка версии
Вкладка **Versions → Create a version**:
- Файл: `chunkswap-1.1.0.jar` (без `-sources`).
- Version number: `1.1.0`
- Version title: `ChunkSwap 1.1.0`
- Release channel: Release
- Loaders: **Fabric**
- Game versions: **1.21.1**
- Dependencies: **Fabric API — Required**; Cloth Config API — Optional; Mod Menu — Optional.
- Changelog: см. ниже.

```
- Added 7 swap modes: whole chunk, layer, sphere, cube, above, below, checkerboard
- Added random block mode
- Added chance setting
- Added settings menu (Mod Menu + Cloth Config) and config/chunkswap.json
- Swap is now spread across ticks to avoid lag
```

## 5. Отправка на проверку
Нажмите **Submit for review** на странице проекта. Модераторы Modrinth проверяют проекты обычно от нескольких часов до нескольких дней. После одобрения проект станет публичным.

## Советы
- Подтвердите адрес e-mail в аккаунте Modrinth, иначе публикация может быть недоступна.
- Не используйте в описании чужие картинки и названия брендов.
- Для новых версий: поменяйте `mod_version` в `gradle.properties`, дождитесь сборки и загрузите новый jar как новую версию.
