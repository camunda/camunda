# Getting started

This guide gets you to a running `webapp/client` on `http://localhost:3000`,
talking to the orchestration-cluster backend on `http://localhost:8080`.

## Prerequisites

- **Node.js** via [`fnm`](https://github.com/Schniz/fnm) (recommended) or
  [`nvm`](https://github.com/nvm-sh/nvm). The repo pins Node in
  `webapp/client/.nvmrc` — `fnm use` / `nvm use` reads it for you.
- **Java** — the backend builds and runs with Maven. Install via
  [SDKMAN](https://sdkman.io) or Homebrew (`brew install openjdk@21`). Check the current version on the project.
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

The dev server opens `http://localhost:3000`. Vite proxies `/v2`, `/login`,
`/logout`, `/session/heartbeat`, `/oauth2`, and `/sso-callback` to `:8080`, so
the frontend talks to the backend you started above.

## Verify

- The dev page renders in the browser.
- `npm run lint` exits clean.
- `npm run typecheck` exits clean.

## Optional: log in with OIDC

Against a backend running with `camunda.security.authentication.method=oidc`,
the dev login page cannot sign you in, because it only shows the username and
password form. Start the login from the browser instead:

1. Set the backend's `camunda.security.authentication.oidc.redirect-uri` to
   `http://localhost:3000/sso-callback`, and register that URI as a valid
   redirect URI for the client in your identity provider.
2. Open `http://localhost:3000/oauth2/authorization/oidc`.
3. Sign in at the identity provider. You are redirected back to
   `http://localhost:3000` with a session.

## Optional: VS Code setup

From the repo root:

```sh
make vscode-sync-all
```

Merges the repository's MCP and editor settings into your VS Code config.
