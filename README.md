# LayerCut

Sideloadable Android photo layer editor — paint, hard/soft erase/cutout, restore brush, magic wand erase, multi-layer transform, undo/redo, and PNG export.

**Package:** `com.stackhousethepoet.layercut`  
**Min SDK:** 26 · **Target SDK:** 35 · **UI:** Jetpack Compose Material 3

## Features (v1)

1. Pick a base photo (Android Photo Picker / `GetContent`)
2. Add additional images as layers
3. Layer list: reorder, visibility, opacity, select active layer; move / scale / rotate the active layer (panel is **hidden by default** — tap **Layers** chip to show)
4. Paint on the active layer
5. Eraser clears alpha (Hard at 100% opacity = full punch-through; Soft below that uses blur)
6. Restore brush paints deleted pixels back from each layer’s original bitmap
7. **Magic** erase: tap to flood-fill contiguous similar-color pixels to transparent (wand-style, **not** ML subject cutout — finish edges with Erase/Restore)
8. Pinch-zoom and pan the canvas (**zoom ~5%–10000%**, i.e. scale `0.05`–`100`; pan is unrestricted so a pixel can sit under your finger)
9. Undo / redo
10. Export flattened PNG to `Pictures/LayerCut` via MediaStore
11. Stylus pressure modulates brush size (and opacity below 100%); at full opacity pressure does not soften alpha

Out of scope: stickers, speech balloons, filters, accounts, ML subject detection.

## Architecture

- **Bitmap-per-layer** document model (`EditorLayer`)
- **BrushEngine** draws with `Canvas` / `Paint`; eraser uses `PorterDuff.Mode.DST_OUT`; restore copies from `originalBitmap` via mask + `SRC_OVER`. Opacity ≥ 98% forces alpha 255 and no `BlurMaskFilter`.
- **MagicFill** contiguous 4-connected RGB flood-fill erase (background thread)
- Each **EditorLayer** keeps an immutable `originalBitmap` for Restore
- **CanvasViewport** for zoom/pan (max scale 100×); layer `LayerTransform` for move/scale/rotate
- **UndoStack** stores ARGB snapshots before destructive edits
- **ExportHelper** flattens visible layers and writes PNG through MediaStore

## Build requirements

- JDK 17+ (JDK 21 works)
- Android SDK with `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`
- Set `sdk.dir` in `local.properties` (or `ANDROID_HOME`)

Example `local.properties`:

```properties
sdk.dir=/workspace/android-sdk
```

## Build debug APK

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # or your JDK 17+
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- Convenience copy: `dist/LayerCut-debug.apk` (after build script / manual copy)

Debug application id: `com.stackhousethepoet.layercut.debug`

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# or
adb install -r dist/LayerCut-debug.apk
```

## Usage tips

- **Pan** tool: drag to pan, pinch to zoom (up to ~10000%); chrome shows current zoom %
- **Paint** / **Erase** / **Restore**: draw on the selected layer; Hard/Soft edge toggle (100% opacity auto-Hard for solid paint / punch-through erase / full restore)
- **Magic**: tap a color region to clear contiguous similar pixels; adjust tolerance; refine with Erase/Restore (not an ML cutout)
- **Move** tool: drag to reposition; pinch to scale; twist to rotate the active layer
- **Layers** chip: show/hide the layer panel (hidden by default to maximize canvas)
- **Export** saves a flattened PNG into Pictures/LayerCut

## License

Source for this project is provided as-is for the LayerCut app owners.
