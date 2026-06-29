# TurboTransfer Packaging

## 1. Build distribution files

Run:

```bat
build-dist.bat
```

This prepares:

- `target\app\TurboTransfer.jar`
- `target\app\lib`

These folders are the local distribution layout used by the launcher scripts.

## 2. Start locally

Desktop shell:

```bat
start-desktop.bat
```

Server mode:

```bat
start-server.bat
```

## 3. Build Windows installer

Run:

```bat
package-installer.bat
```

The script uses `jpackage` and produces a Windows installer under `dist\`.

## Notes

- Global runtime configuration is centralized in `src\main\resources\application.properties`.
- The installer packages `target\app\TurboTransfer.jar` and `target\app\lib` together, and they are installed under the selected install directory.
- When started from the bat scripts, the install root is the script directory itself.
- When started from the installed EXE, the install root is auto-detected from the launcher location.
- Logs are written to `<install-dir>\log\launcher.log`.
- Downloads, cache, and other runtime files default to subdirectories under the same install root.
- If you change launcher classes or dependencies, rebuild distribution files before packaging.
