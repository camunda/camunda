# Pinned Spectral toolchain

Previously the `Lint / C8 REST OpenAPI` CI job linted the OCA OpenAPI
spec by running (with the CLI version inlined here for clarity — at
the time the workflow used a `SPECTRAL_VERSION` env var):

```sh
npm install -g "@stoplight/spectral-cli@6.16.0"
spectral lint ...
```

The top-level CLI version was pinned, but `npm install -g` does not
lock the transitive dependency tree — the CLI re-resolved sub-packages
to whatever was current on npm at install time.

On 2026-05-21, `@stoplight/spectral-rulesets@1.22.3` was published with a
regression that crashes the linter on any spec containing a `null` value
in a `nullable: true` example payload (e.g. the `cloud` example block in
`zeebe/gateway-protocol/src/main/proto/v2/system.yaml`, where
`organizationId`, `clusterId`, `stage`, `mixpanelToken`, and
`mixpanelAPIHost` are all `null` for the self-managed-deployment case).
The crash surfaces as:

```
Error #1: Cannot read properties of null (reading 'enum')
```

Since the same Spectral CLI version had previously passed CI with
`spectral-rulesets@1.22.2`, the regression is in the transitive dep,
not in the spec.

## How this directory unblocks CI

`package.json` declares Spectral as a local dependency and uses npm
`overrides` to pin the two transitively-resolved Spectral sub-packages
that were republished today to the last known-good versions:

|                package                 | pinned version | last current at PR #52280 |
|----------------------------------------|----------------|---------------------------|
| `@stoplight/spectral-rulesets`         | `1.22.2`       | 2026-05-12                |
| `@stoplight/spectral-ruleset-migrator` | `1.12.0`       | 2026-04-13                |

`package-lock.json` records the full transitive resolution so subsequent
`npm ci` runs are reproducible.

`.github/workflows/ci.yml` is updated to install via `npm ci` in this
directory and invoke the linter (from the repo root) via
`.github/spectral/node_modules/.bin/spectral` instead of a global install.

## Bumping the CLI version

The Spectral CLI version is declared in `package.json` here, not in
`ci.yml`. Renovate manages this `package.json` natively as it does any
other npm manifest in the repo, so version bumps land via the usual
Renovate PR flow. To bump manually, edit
`dependencies."@stoplight/spectral-cli"` and run `npm install` here to
regenerate `package-lock.json`.

## When to remove the pin

When `@stoplight/spectral-rulesets` ships a fixed version (track
upstream on the Spectral issue tracker), drop the `overrides` block
from `package.json`, regenerate `package-lock.json`, and the toolchain
will follow direct dependency resolution again.
