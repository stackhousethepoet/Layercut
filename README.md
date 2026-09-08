# LayerCut

Sideloadable Android photo layer editor — paint, soft erase/cutout, multi-layer transform, undo/redo, and PNG export.

**Package:** `com.stackhousethepoet.layercut`  
**Min SDK:** 26 · **Target SDK:** 35 · **UI:** Jetpack Compose Material 3

## Features (v1)

1. Pick a base photo (Android Photo Picker / `GetContent`)
2. Add additional images as layers
3. Layer list: reorder, visibility, opacity, select active layer; move / scale / rotate the active layer
4. Paint on the active layer
5. Soft round eraser clears alpha (precision cutout)
6. Pinch-zoom and pan the canvas
7. Undo / redo
8. Export flattened PNG to `Pictures/LayerCut` via MediaStore
9. Stylus pressure modulates brush size/opacity when available (finger works without a stylus)

Out of scope: stickers, speech balloons, filters, accounts.

## Architecture

- **Bitmap-per-layer** document model (`EditorLayer`)
- **BrushEngine** draws with `Canvas` / `Paint`; eraser uses `PorterDuff.Mode.DST_OUT`
- **CanvasViewport** for zoom/pan; layer `LayerTransform` for move/scale/rotate
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

- **Pan** tool: drag to pan, pinch to zoom the canvas
- **Paint** / **Erase**: draw on the selected layer; stylus pressure is respected when present
- **Move** tool: drag to reposition; pinch to scale; twist to rotate the active layer
- Use the layer panel to toggle visibility, opacity, and stacking order
- **Export** saves a flattened PNG into Pictures/LayerCut

## License

Source for this project is provided as-is for the LayerCut app owners.
