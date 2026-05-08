# Getting started

This guide gets you to a running `webapp/client` on `http://localhost:3000`,
talking to the orchestration-cluster backend on `http://localhost:8080`.

## Prerequisites

- **Node.js** via [`fnm`](https://github.com/Schniz/fnm) (recommended) or
  [`nvm`](https://github.com/nvm-sh/nvm). The repo pins Node in
  `webapp/client/.nvmrc` — `fnm use` / `nvm use` reads it for you.
- **Java 21** — the backend builds and runs with Maven. Install via
  [SDKMAN](https://sdkman.io) or Homebrew (`brew install openjdk@21`).
- **Docker** — used by `make env-up` to run Elasticsearch, and by Playwright's
  containerized browser for visual-regression tests.
- **make** — wraps backend startup (`make env-up` / `make env-down`) and VS Code
  config sync (`make vscode-sync-all`). Pre-installed on macOS and most Linux;
  Windows users install via WSL or `choco install make`.

## Clone & install

```sh
git clone https://github.com/camunda/camunda.git
cd camunda/webapp/client
fnm use            # or: nvm use
npm ci
```

The workspace install covers all `packages/*` and `apps/*`.

## Run the backend

From `webapp`:

```sh
make env-up
```

This boots Elasticsearch (Docker) and the orchestration-cluster backend on
`http://localhost:8080`, seeded with a `demo` / `demo` user. Tear it down with:

```sh
make env-down
```

## Run the frontend

From `webapp/client`:

```sh
npm run dev:oc
```

The dev server opens `http://localhost:3000`. Vite proxies `/v2`, `/login`, and
`/logout` to `:8080`, so the frontend talks to the backend you started above.

## Verify

- The dev page renders in the browser.
- `npm run lint` exits clean.
- `npm run typecheck` exits clean.

## Optional: VS Code setup

From the repo root:

```sh
make vscode-sync-all
```

Merges the repository's MCP and editor settings into your VS Code config.
