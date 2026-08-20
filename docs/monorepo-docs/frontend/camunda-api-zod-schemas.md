# Camunda API Zod schemas

`@camunda/camunda-api-zod-schemas` is a community-driven open-source
package that provides [Zod](https://zod.dev/) schemas and TypeScript
types for the Camunda 8 REST API. It helps developers build robust,
type-safe applications when interacting with Camunda 8.

Official Camunda 8 REST API reference:
[docs.camunda.io](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/camunda-api-rest-overview/).

Inside this monorepo, the package is consumed directly via the npm
workspace by `@camunda/orchestration-cluster-webapp`, and via published
versions by the legacy `operate/client`, `tasklist/client`, and
`identity/client` frontends.

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
`/8.9`, or `/8.10`.

For the full list of exported schemas and types, refer to the source
under `packages/camunda-api-zod-schemas/lib/` — for example
`packages/camunda-api-zod-schemas/lib/8.8/index.ts`.

## Publishing a new version

1. Increment the version in the `camunda-api-zod-schemas` `package.json`
   (this is manual for now; we plan to automate it in the future), update the dependency
   version in any consumer npm workspace packages, run `npm i`, and
   push the changes to `main`.
2. Run the [Publish Zod Schemas to npm](https://github.com/camunda/camunda/actions/workflows/publish-zod-schemas.yml)
   GitHub Action.
   - The dry-run option is enabled by default. Uncheck it to publish
     the new version.
3. Update the `@camunda/camunda-api-zod-schemas` dependency version in
   Operate, Admin, and Tasklist.

### When the schema update is part of a new feature

1. Update the schema and changelog, increment the version in
   `package.json`, and open a PR.
2. Merge the PR to `main`.
3. Immediately publish the new version from `main` using the GitHub
   Action above.
4. Open follow-up PRs in Operate, Tasklist, and Admin bumping
   `@camunda/camunda-api-zod-schemas` to the new version.
5. Use the updated schema in the feature PR.
