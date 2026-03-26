# viaPanel API Reference

This document describes the public API exposed by the `viapanel` mod so other mods can attach their own per-mod config UI to `/viapanel`.

## Overview

`viapanel` is a server-side Fabric mod that provides a shared clickable chat panel.

Main flow:

1. Player runs `/viapanel`.
2. viaPanel lists all installed Fabric mods.
3. If a mod registered a `ViaPanelProvider`, the entry becomes clickable.
4. Clicking opens that mod's sections and editable fields.
5. Field edits are written back using reflection and `save()`.

## Dependency setup (Gradle)

In your mod's `build.gradle`:

```gradle
dependencies {
    // Option A: Maven artifact (recommended once published)
    // modImplementation "com.viameowts.viapanel:viapanel:<version>"

    // Option B: local standalone jar
    modCompileOnly files("libs/viapanel-<version>.jar")
}
```

`viapanel` is intended to be a standalone mod project and jar, not a Gradle submodule of your mod.

In your `fabric.mod.json`, add:

```json
"depends": {
  "viapanel": "*"
}
```

## Package and types

Public API package:

- `com.viameowts.viapanel.api.ViaPanelApi`
- `com.viameowts.viapanel.api.ViaPanelProvider`
- `com.viameowts.viapanel.api.ViaPanelSection`

### `ViaPanelApi`

Registry for providers.

Methods:

- `register(ViaPanelProvider provider)`
- `unregister(String modId)`
- `ViaPanelProvider getProvider(String modId)`
- `List<ViaPanelProvider> getProviders()`

`modId` must match your Fabric mod id (from `fabric.mod.json`).

### `ViaPanelSection`

Record describing one panel page/section.

```java
public record ViaPanelSection(String id, Text title, List<String> fields)
```

Fields:

- `id`: section id used in command path (`/viapanel <mod> <section>`)
- `title`: page title rendered in chat
- `fields`: public config field names shown/edited on that page

### `ViaPanelProvider`

Main integration contract.

Required methods:

- `String modId()`
- `Text modDisplayName()`
- `Text panelTitle()`
- `boolean hasPermission(ServerCommandSource source)`
- `Class<?> configClass()`
- `Object configInstance()`
- `List<ViaPanelSection> sections()`

Optional customization hooks (have defaults):

- `Text fieldDisplayName(String fieldName)`
- `Text fieldDescription(String fieldName)`
- `Text toggleHintText()`
- `Text editHintText()`
- `Text savedSuffixText()`
- `Text fieldNotBooleanText()`
- `Text unknownFieldText()`
- `Text invalidNumberText()`
- `void reload(ServerCommandSource source)`
- `Text reloadDoneText()`
- `void onFieldUpdated(String fieldName, ServerCommandSource source)`
- `void applyGlobalLanguage(String languageCode, ServerCommandSource source)`

When any provider field named `defaultLanguage` is changed through viaPanel (`en` / `ru`), viaPanel broadcasts the new language to all registered providers via `applyGlobalLanguage(...)`.

## Field edit behavior

viaPanel uses reflection against `configClass()` and `configInstance()`.

Supported field types:

- `boolean`
- `int`
- `double`
- `String`

Unsupported types are shown as `(unsupported)` and are read-only in UI.

On write:

1. Field value is updated via reflection.
2. viaPanel tries to call `save()` on the config object (no-arg method).
3. `onFieldUpdated(fieldName, source)` is called.

Because of step 2, your config class should expose a public `save()` method.

## Reload behavior

When player clicks reload:

- viaPanel calls `provider.reload(source)`.
- viaPanel sends `provider.reloadDoneText()`.

Use this hook to:

- re-read config from disk,
- refresh caches,
- update in-memory UI/game state.

## Command surface

Provided by viaPanel:

- `/viapanel` — list installed mods
- `/viapanel <modId>` — open mod panel
- `/viapanel <modId> <sectionId>` — open section
- `/viapanel toggle <modId> <field>` — toggle boolean field
- `/viapanel set <modId> <field> <value>` — set string/number/boolean
- `/viapanel reload <modId>` — invoke provider reload hook
- `/viapanel lang <ru|en>` — set global viaPanel language and propagate to all registered providers

## Minimal provider example

```java
public class ExamplePanelProvider implements ViaPanelProvider {
    @Override
    public String modId() { return "examplemod"; }

    @Override
    public Text modDisplayName() { return Text.literal("Example Mod"); }

    @Override
    public Text panelTitle() { return Text.literal("Example Mod Settings"); }

    @Override
    public boolean hasPermission(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    @Override
    public Class<?> configClass() { return ExampleConfig.class; }

    @Override
    public Object configInstance() { return ExampleMod.CONFIG; }

    @Override
    public List<ViaPanelSection> sections() {
        return List.of(
            new ViaPanelSection("general", Text.literal("General"), List.of("enabled", "radius", "prefix"))
        );
    }

    @Override
    public void reload(ServerCommandSource source) {
        ExampleMod.CONFIG = ExampleConfig.load();
    }
}
```

Register in server init:

```java
ViaPanelApi.register(new ExamplePanelProvider());
```

## viaStyle implementation notes

Current workspace includes a concrete adapter:

- `com.viameowts.viastyle.ViaStylePanelProvider`

It maps existing viaStyle fields/pages to API sections and preserves current behavior:

- language switch on `defaultLanguage` update,
- tab/nametag refresh after related field changes,
- full config reload path.

## Compatibility guidance

- Keep config fields public if you want auto-edit support.
- Preserve stable field names to avoid breaking existing panel sections.
- For complex types (`List`, `Map`, nested objects), expose simplified bridge fields or custom command(s).
- Treat `fieldDescription` as user-facing docs; keep it concise and actionable.
