# Runtime Rules

## Intended Use

C8Run is for local development and testing only. It is not a production deployment mechanism.

## Config Precedence

Always load `configuration/application.yaml` first, then append the user-provided `--config` file or directory last. User config always wins.

## H2 Secondary Storage

H2 is the default secondary storage for C8Run. It is supported for local development convenience only — not for production workloads. The default URL is `jdbc:h2:file:./camunda-data/h2db`, so data persists across stop/start cycles. On stop, C8Run only deletes the H2 data directory when the active RDBMS URL is explicitly an in-memory H2 URL (`jdbc:h2:mem`). File-based H2 (the default) and other storage types are not cleaned up. See [Configuration — H2 Data Directory Cleanup](configuration.md#h2-data-directory-cleanup) for the full decision logic.

## Connectors Launcher Compatibility

| Connector bundle version | Launcher |
|---|---|
| `8.9.0` and newer (including snapshots) | Spring Boot `PropertiesLauncher` |
| Older than `8.9.0` | Legacy connector runtime main class |

Do not change this version gate without verifying both launcher paths still work.

## Default Ports

| Endpoint | Default port |
|---|---|
| Web / REST | `8080` |
| Connectors health | `8086` |
| Camunda metrics | `9600` |
| Zeebe gRPC | `26500` |

The `--port` flag changes the main web/REST port. If the user did not set `CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS`, Connectors defaults to the selected Camunda port.

**Connectors health port is always `8086`** — it is hardcoded and not affected by `--port`.

## Health Check Timeout

Startup health checks use 24 retries with a 14-second delay between attempts (~5.6 minutes total). If Camunda does not become healthy within this window, C8Run reports failure. Connectors health checks are not yet implemented — the check currently returns success immediately without querying port 8086.

## Startup URL and Quickstart Marker

On the first run of C8Run 8.9.0+, the browser opens the Camunda quickstart docs URL instead of Operate. After that first run, a marker file is written and subsequent runs open Operate directly.

The marker path is resolved in this order:
1. `os.UserConfigDir()` — platform-specific (`~/Library/Application Support` on macOS, `%AppData%` on Windows, `~/.config` on Linux)
2. `~/.config/camunda/c8run/` — home-dir fallback if `UserConfigDir` fails
3. C8Run base directory — last resort if home dir is also unavailable

To force the quickstart URL to appear again, delete the marker file from whichever location was resolved.

## Connectors Startup

C8Run starts Connectors from the connector bundle plus `custom_connectors/*`. Connectors runs alongside the main Camunda process and shares the same shutdown lifecycle.

## Logs and PIDs

- `log/camunda.log` — Camunda process output
- `log/connectors.log` — Connectors process output
- PID files are written on start and cleaned on stop

## Run and Stop

From a prepared C8Run directory:

```bash
# Linux/macOS
./c8run start
./c8run stop

# Unix convenience wrappers
./start.sh
./shutdown.sh

# Windows
c8run.exe start
c8run.exe stop
```

Common start flags:

```bash
./c8run start --port 9090
./c8run start --config ./my-config.yaml
./c8run start --extra-driver ./driver.jar
./c8run start --log-level debug
./c8run start --username demo --password demo
./c8run start --startup-url http://localhost:8080/operate
```

Use `./c8run help` to print all supported commands and flags. When running in the foreground, `Ctrl+C` initiates graceful shutdown.
