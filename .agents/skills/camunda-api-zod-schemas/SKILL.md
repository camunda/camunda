---
name: camunda-api-zod-schemas
description: Use when you change the OpenAPI spec in zeebe/gateway-protocol/src/main/proto/v2/, or schemas, types, or endpoints in @camunda/camunda-api-zod-schemas (webapp/client/packages/camunda-api-zod-schemas/); when a spec change breaks the webapp types; when you add a module or a version tree; or when you prepare a release of the package.
---

# Camunda API Zod schemas

`PKG` is `webapp/client/packages/camunda-api-zod-schemas/`. The spec is `zeebe/gateway-protocol/src/main/proto/v2/`.

The package has two types of version tree in `PKG/lib/<version>/`:

- **Generated tree** (8.11). Scripts generate `lib/8.11/gen/` from the spec. The modules give public names to the gen schemas.
- **Manual trees** (8.8, 8.9, 8.10). People write the Zod schemas manually.

For the rules, the pods, and the changelog template, refer to [reference.md](reference.md).

## Rules

- The spec is the reference. If the gen output does not agree with the backend, correct the spec.
- Do not change `gen/` or `specs/`. Git ignores them, and the scripts write them again.
- Keep the public names that `lib/<version>/index.ts` exports.
- A change in one version tree does not go into the other trees.
- If a spec change causes type errors in the webapp, correct the webapp in the same PR. Ask the pod that owns the changed code to review the PR.

## Procedure 1: Change the spec

1. Change the YAML files of the spec.
2. In `webapp/client/`, generate the schemas and build the package:

   ```bash
   npm run prepare -w @camunda/camunda-api-zod-schemas
   ```

3. Do Procedure 5.
4. If the webapp has type errors, correct the webapp code and the MSW mocks. Do not use `@ts-expect-error` or casts.
5. Tell the user which pods must review the PR. Refer to "Pods" in [reference.md](reference.md).
6. Do Procedure 4.

NOTE: The CI job "Check / C8 REST OpenAPI / Webapp Client types" does steps 2 and 3 for each spec change. At this time, the job does not block the merge.

## Procedure 2: Change a module

1. Find the version trees:
   - The generated tree gets all spec changes. Change its module only to add or remove a public name.
   - Change a manual tree only if the spec marks the change with its release line (`x-added-in-version`). Also change the higher manual trees.
2. In a generated tree, use the rules in "Generated trees" in [reference.md](reference.md).
3. In a manual tree, use the rules in "Manual trees" in [reference.md](reference.md).
4. For a new endpoint, write the endpoint object manually:

   ```ts
   const getAgentInstance = {
   	method: 'GET',
   	getUrl: ({agentInstanceKey}) => `/${API_VERSION}/agent-instances/${agentInstanceKey}` as const,
   } as const satisfies Endpoint<{agentInstanceKey: string}>;
   ```

5. Add the new names to the `export {…}` and `export type {…}` blocks of the module.
6. In `lib/<version>/index.ts`, add the endpoint to the `endpoints` object. Export the new schemas and types.
7. For a new module, also add an entry to `build.lib.entry` in `PKG/vite.config.ts` and to `exports` in `PKG/package.json`.
8. Do Procedure 5 and Procedure 4.

CAUTION: The build stops if it finds a circular import. Move shared schemas to a helper module, for example `processes.ts`.

## Procedure 3: Add a version tree

Do this procedure when `webapp/client/package.json` shows a new release line, for example `8.12.0-SNAPSHOT`.

1. In `PKG/scripts/supported-versions.js`:
   - For 8.11, set `branch` to `'stable/8.11'` and `input` to `'spec-snapshots/8.11/rest-api.yaml'`.
   - Add 8.12 with `branch: 'main'`, `input: 'specs/8.12/rest-api.yaml'`, and `output: 'lib/8.12/gen'`.
2. In `PKG`, copy the spec of 8.11 into the package. Then the build does not need the network:

   ```bash
   npm run download-specs -- -v 8.11
   mkdir -p spec-snapshots && cp -R specs/8.11 spec-snapshots/8.11
   ```

3. In the `prepare` script of `PKG/package.json`, change `generate-schemas -- -v current` to `generate-schemas -- -v 8.11 -v current`.
4. Add `packages/camunda-api-zod-schemas/spec-snapshots/` to `webapp/client/.prettierignore`.
5. Copy `lib/8.11` to `lib/8.12`. Copy the 8.11 entries in `vite.config.ts` and in `exports` of `PKG/package.json`, and change the version.
6. Add the new version to `docs/monorepo-docs/frontend/camunda-api-zod-schemas.md`.
7. Do Procedure 5 and Procedure 4.

NOTE: If the spec in `stable/8.11` changes, do step 2 again.

## Procedure 4: Increase the version

1. If `npm view @camunda/camunda-api-zod-schemas versions --json` shows the version in `PKG/package.json`, increase the last number by one.
2. Write the new version in `PKG/package.json`, `apps/orchestration-cluster-webapp/package.json`, and `packages/c8-mocks/package.json`.
3. In `webapp/client/`, do `npm i`. Do not change `package-lock.json` manually.
4. Add a section at the top of `PKG/CHANGELOG.md`. Use the template in [reference.md](reference.md).

## Procedure 5: Examine the change

Do these commands in `webapp/client/`. If a command shows errors, correct the cause.

```bash
npm run prepare -w @camunda/camunda-api-zod-schemas   # if the spec or the scripts changed
npm run typecheck                                     # all workspaces, also the consumers
npm run lint
npm run test:unit -w @camunda/orchestration-cluster-webapp -- --run src/<changed folder>
```

## Procedure 6: Publish

CAUTION: A publish to npm is permanent. Do not start the workflow yourself.

1. After the merge, tell the user to start the workflow "Publish Zod Schemas to npm" from `main` with `dry_run` set to false.
2. Offer PRs that set the new version in `operate/client` and `identity/client`. In each folder, do `npm i` and `npm run lint`.

NOTE: `min-release-age=1` in `.npmrc` can stop `npm i` for one day.
