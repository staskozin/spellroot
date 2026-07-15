# Spellroot

Spellroot — мод для Minecraft 26.2 на NeoForge. Проект использует Java 25 и Gradle Wrapper 9.2.1.

## Сборка

На Windows:

```powershell
.\gradlew.bat build
```

На Linux и macOS:

```bash
./gradlew build
```

Готовый JAR создаётся в `build/libs/`. Сборки с коммитов, не отмеченных релизным тегом, получают версию вида `0.2.0-dev.a1b2c3d`; они не публикуются как GitHub Release.

## Conventional Commits

Все обычные коммиты в `main` должны соответствовать формату:

```text
type(optional-scope): краткое описание
```

Основные типы и влияние на версию:

- `feat:` — minor;
- `fix:`, `perf:`, `revert:` — patch;
- `type!:` или footer `BREAKING CHANGE:` — major;
- `docs:`, `refactor:`, `test:`, `build:`, `ci:`, `chore:`, `style:` попадают в changelog, но сами не создают новую версию.

Проверить историю после последнего релиза можно командой:

```powershell
.\gradlew.bat validateConventionalCommits
```

Merge-коммиты при проверке и генерации changelog игнорируются.

## Ежедневный flow

Разработка ведётся напрямую в `main`:

```powershell
git pull --ff-only
# изменить код
git add .
git commit -m "feat(spells): добавлено новое заклинание"
git push origin main
```

GitHub Actions проверяет Conventional Commits, компилирует проект и собирает JAR для каждого push в `main`.

## Выпуск версии

Перед выпуском синхронизируйте ветку и теги:

```powershell
git pull --ff-only
git fetch --tags origin
.\gradlew.bat bump
```

`bump` требует чистую ветку `main`, запускает полный `build`, вычисляет следующую версию, обновляет `gradle.properties` и `CHANGELOG.md`, создаёт commit `chore(release): vX.Y.Z` и аннотированный тег `vX.Y.Z`. Команда ничего не отправляет в `origin`.

Если нужен осознанный ручной уровень версии, используйте:

```powershell
.\gradlew.bat bump -PreleaseType=major
.\gradlew.bat bump -PreleaseType=minor
.\gradlew.bat bump -PreleaseType=patch
```

Override не может быть ниже уровня, требуемого коммитами. Например, breaking change нельзя выпустить как patch.

Проверьте созданные commit и tag, затем отправьте их одной командой:

```powershell
git show --stat HEAD
git tag --points-at HEAD
git push origin main --follow-tags
```

Push корректного тега `vX.Y.Z` запускает отдельный workflow: он повторно проверяет связь тега с `main` и `mod_version`, собирает проект на JDK 25 и публикует GitHub Release с `spellroot-X.Y.Z.jar` и соответствующим разделом `CHANGELOG.md`.
