# Publishing ChainMine Core to Modrinth

This folder contains everything you need to publish the mod on
[Modrinth](https://modrinth.com/). Two files to upload on the project page
and a third (in repo root) for the release entry.

## Files in this folder

| File | Purpose | Where to upload |
| --- | --- | --- |
| `icon.png` | Project icon (512×512, red diamond) | Project Settings → Icon |
| `description.md` | Full project page body (English + brief Chinese) | Project Settings → Description |

## Files in repo root

| File | Purpose |
| --- | --- |
| `CHANGELOG.md` | Use the `[1.0.0] - 2026-08-24` section as the version's changelog |

## Manual upload (recommended, ~10 min)

1. **Create the project** at <https://modrinth.com/dashboard/projects> → **New Project**
   - Slug: `chainmine-core`
   - Name: `ChainMine Core`
   - Summary: *Mine entire veins in one swing — regular 3×3×3 shape, empty-hand support, server-authoritative. Fabric 1.21.1.*
   - Type: **Mod**
   - Client-side: **Unsupported** (singleplayer requires the mod on client anyway) → leave the default "Singleplayer: required" which Modrinth picks automatically
   - Server-side: **Required**
   - License: **MIT**

2. **Project icon**: upload `docs/modrinth/icon.png`

3. **Project description**: copy the entire content of `docs/modrinth/description.md`
   (Modrinth supports full Markdown)

4. **Create a version** at <https://modrinth.com/dashboard/projects/chainmine-core/version/new>
   (the slug becomes the URL after project creation)
   - Version number: `1.0.0`
   - Version title (optional): `Initial release`
   - Changelog: paste the `[1.0.0]` section from `CHANGELOG.md`
   - Minecraft versions: `1.21.1`
   - Mod loaders: `fabric`
   - Upload the **primary file**: `build/libs/chainmine-core-1.0.0.jar`
   - Dependencies: click "Add dependency" →
     - Project ID / slug: `fabric-api`
     - Version range: `*` (any compatible)
     - Type: `required`
   - Click **Save**

5. **Set the version as the primary release** if it isn't auto-selected

6. Done! The mod is now public on Modrinth at
   `https://modrinth.com/mod/chainmine-core`.

## Automatic upload (advanced)

Modrinth supports an [API](https://docs.modrinth.com/api/) for automated
publishing. To use it you need a **Personal Access Token** from
<https://modrinth.com/settings/pats> (with `create_projects`, `upload_version`
and `upload_assets` scopes).

If you provide the token, the assistant can do the full publish flow
(create project, upload icon, upload jar with metadata, set dependencies)
via `curl` against the Modrinth API. Keep the token secret — don't paste
it in shared chat channels.

## Build artifacts

After `./gradlew build`, the mod jar lives at:

```
build/libs/chainmine-core-1.0.0.jar          # the mod itself
build/libs/chainmine-core-1.0.0-sources.jar  # optional, for deobfuscation
```

Only the first one is needed for Modrinth upload.
