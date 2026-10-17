# Camunda API Zod schemas

`@camunda/camunda-api-zod-schemas` is a community-driven open-source
package that provides [Zod](https://zod.dev/) schemas and TypeScript
types for the Camunda 8 REST API. It helps developers build robust,
type-safe applications when interacting with Camunda 8.

Official Camunda 8 REST API reference:
[docs.camunda.io](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/camunda-api-rest-overview/).

Inside this monorepo, the package is consumed directly via the npm
workspace by `@camunda/orchestration-cluster-webapp`, and via published
versions by the legacy `operate/client` and `identity/client` frontends.

## Installation

```bash
# pnpm
pnpm add @camunda/camunda-api-zod-schemas

# npm
npm install @camunda/camunda-api-zod-schemas

# yarn
yarn add @camunda/camunda-api-zod-schemas
```

## Usage

The library exports modules that correspond to the different parts of
the Camunda API. Import schemas and types from the main package or from
a specific version sub-module — `@camunda/camunda-api-zod-schemas/8.8`,
`/8.9`, `/8.10`, or `/8.11`.

For the full list of exported schemas and types, refer to the source
under `packages/camunda-api-zod-schemas/lib/` — for example
`packages/camunda-api-zod-schemas/lib/8.8/index.ts`.

## Generated schemas

The schemas and types of 8.11 are generated from the OpenAPI spec in
`zeebe/gateway-protocol/src/main/proto/v2/`. The versions 8.8, 8.9, and 8.10 are written manually.

- `npm ci` in `webapp/client/` generates the schemas from the local spec and builds the package.
- To generate them again after a spec change, use
  `npm run prepare -w @camunda/camunda-api-zod-schemas`.
- The generated files are in `lib/<version>/gen/`. Git ignores them. Do not change them.

When a spec change breaks the types of the orchestration cluster webapp, correct the webapp in the
same PR. Ask the pod that owns the changed code to review the PR. The CI job
"Check / C8 REST OpenAPI / Webapp Client types" examines each spec change.

## Publishing a new version

1. Increment the version in the `camunda-api-zod-schemas` `package.json`, update the dependency
   version in any consumer npm workspace packages, run `npm i`, and
   push the changes to `main`.
2. Run the [Publish Zod Schemas to npm](https://github.com/camunda/camunda/actions/workflows/publish-zod-schemas.yml)
   GitHub Action.
   - The dry-run option is enabled by default. Uncheck it to publish
     the new version.
3. Update the `@camunda/camunda-api-zod-schemas` dependency version in the legacy Operate and Admin
   frontends. Tasklist consumes the workspace package through the orchestration-cluster webapp.

### When the schema update is part of a new feature

1. Update the schema and changelog, increment the version in
   `package.json`, and open a PR.
2. Merge the PR to `main`.
3. Immediately publish the new version from `main` using the GitHub
   Action above.
4. Open follow-up PRs in the legacy Operate and Admin frontends bumping
   `@camunda/camunda-api-zod-schemas` to the new version.
5. Use the updated schema in the feature PR.
