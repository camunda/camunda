# Camunda 8 Run

C8 Run is a packaged distribution of Camunda 8, which allows you to spin up Camunda 8 within seconds.
It packages the local Java runtime only; the Docker Compose distribution is published separately.

Please refer to the [local installation with Camunda 8 Run guide](https://docs.camunda.io/docs/next/self-managed/quickstart/developer-quickstart/c8run/) for further details.

## Default secondary storage

Camunda 8 Run now starts with H2 as the secondary storage backend. No additional configuration or flags are required. Running `./c8run start` launches a full stack backed by H2 for local development and testing scenarios. H2 is not supported for production workloads.

1. Start Camunda 8 Run:

   ```bash
   ./c8run start
   ```
2. Stop Camunda 8 Run as usual:

   ```bash
   ./c8run stop
   ```

## CI requirement for merging

Only CI checks related to C8Run (those with "c8run" in the name) and CI runs marked as `required` are needed to merge. Non-C8Run-related CI checks can be ignored.

## Build C8run locally

### 1. Install Go

Go **1.25 or newer** is required (the `go.mod` minimum). Verify any existing installation with `go version`.

### 2. Configure LDAP credentials in `.env`

Open `c8run/.env` and add the following two lines using your Camunda LDAP credentials:

```dotenv
JAVA_ARTIFACTS_USER=<firstname.lastname>
JAVA_ARTIFACTS_PASSWORD=<your current Okta password>
```

### 3. Build the C8run binary

From the `c8run/` directory, run the appropriate command for your platform:

**Windows:**

```bash
go build -o c8run.exe ./cmd/c8run
```

**Linux / macOS:**

```bash
go build -o c8run ./cmd/c8run/
```

### 4. Package the distribution

```bash
./package.sh
```

### 5. Start Camunda 8 Run

```bash
./start.sh
```

### Connectors launcher

C8Run automatically starts the connectors runtime through Spring Boot's `PropertiesLauncher` for connector bundles versioned 8.9.0 or newer (including snapshots). Older bundles continue to run via the legacy `JarLauncher`, so you can switch versions in `.env` without extra configuration.

If you want to run your own connectors runtime, start C8Run with `./c8run start --disable-connectors` to skip launching the bundled connectors jar.
