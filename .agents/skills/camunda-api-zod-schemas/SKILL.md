---
name: camunda-api-zod-schemas
description: Use when you add, change, or remove schemas, fields, enums, filters, sort fields, or endpoints in @camunda/camunda-api-zod-schemas (webapp/client/packages/camunda-api-zod-schemas/); when you align the package with the OpenAPI spec in zeebe/gateway-protocol/src/main/proto/v2/; when you add a module or a release-line version tree; or when you increase the package version and prepare a release.
---

# Camunda API Zod schemas

This skill tells you how to change the package `@camunda/camunda-api-zod-schemas`.
The package is in `webapp/client/packages/camunda-api-zod-schemas/`. This skill calls this folder `PKG`.

The package contains Zod schemas, TypeScript types, and endpoint objects for the Camunda 8 REST API (v2).
People write the package manually. No tool generates the package from the OpenAPI spec.

NOTE: The manual schemas are temporary. Issue [#39752](https://github.com/camunda/camunda/issues/39752) will add automatic generation from the OpenAPI spec. Before you start, read the status of the issue. If the issue is closed, examine the package for a generation script before you change the schemas manually.

For the mapping tables, the names, and the changelog template, refer to [reference.md](reference.md).

## Terms

| Term         | Meaning                                                                        |
| ------------ | ------------------------------------------------------------------------------ |
| Spec         | The OpenAPI files in `zeebe/gateway-protocol/src/main/proto/v2/`               |
| Version tree | A folder `PKG/lib/<version>/` for one release line, for example `8.10`         |
| Module       | One file in a version tree, for example `agent-instance.ts`                    |
| Consumer     | A package that uses this package, for example the orchestration cluster webapp |

## Rules

- The spec is the reference. Do not add a field, enum value, or endpoint that is not in the spec.
- Each version tree is a full copy. A change in one tree does not go into the other trees.
- Keep the Camunda license header at the top of each file.
- Put `type X = z.infer<typeof xSchema>;` immediately after each schema.
- Use one `export {…}` block and one `export type {…}` block at the end of each file. Do not use inline `export`.
- Use the helpers in `common.ts`. Do not write a new filter or query helper if a helper can do the work.
- Do not change the `common.ts` imports of a module. Tree 8.8 and some other modules import `lib/common.ts`. The other modules import `./common`.
- Consumers read `PKG/dist/`, not `PKG/lib/`. After you change `lib/`, build the package again.
- If a different task (for example, a UI feature) causes the schema change, tell the user before you change the package.

## Procedure 1: Find the spec

1. Find the API path in `zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml`.
2. Find the domain file in the `$ref` of the path, for example `agent-instances.yaml`.
3. In the domain file, find the schemas of the entity:
   - `<Entity>Result`: the response item.
   - `<Entity>Filter`: the search filter.
   - `<Entity>SearchQuerySortRequest`: the sort fields.
   - `<Entity>...Enum`: the enum values.
4. Find the version markers. Operations use `x-added-in-version`. Properties use `x-properties-added-in-version`.

NOTE: Do not use the spec copies in `target/` or `dist/` folders. These copies can be old.

```bash
grep -n "agent-instances" zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml
grep -n "AgentInstanceMetrics:" -A40 zeebe/gateway-protocol/src/main/proto/v2/agent-instances.yaml
```

## Procedure 2: Select the version trees

1. Find the release line of the change. Use the version markers from Procedure 1.
2. If the spec has no version marker, use the release line of `main`. The `<version>` in the root `pom.xml` shows it. For example, `8.11.0-SNAPSHOT` is release line 8.11.
3. Change the version tree of that release line.
4. Also change all version trees that have a higher version.
5. Do not change version trees that have a lower version. Change them only if the task tells you to do this.
6. If no version tree exists for the release line, do Procedure 6 first.
7. Tell the user which version trees you selected, and why.

Example: the agent instance API has `x-added-in-version: "8.10"`.
A new 8.10 property goes into `lib/8.10/agent-instance.ts` and `lib/8.11/agent-instance.ts`.

```bash
grep -m1 -n "<version>" pom.xml
ls webapp/client/packages/camunda-api-zod-schemas/lib
```

## Procedure 3: Change a field or an enum

1. Do Procedure 1 and Procedure 2.
2. In each selected version tree, open `lib/<version>/<module>.ts`.
3. Change the schema. Use the table "Spec to Zod" in [reference.md](reference.md).
4. If the spec changes the filter, also change the filter schema.
5. If the spec changes the sort fields, also change the `sortFields` array.
6. If you remove a field or an enum value, find the consumer code that uses it. Change that code.
7. Compare the module in all selected trees. Make sure that the change is the same in each tree.
8. Do Procedure 7 and Procedure 8.

Example (PR #62253):

```diff
 const agentInstanceMetricsSchema = z.object({
 	inputTokens: z.number(),
 	outputTokens: z.number(),
+	reasoningTokenCount: z.number(),
+	cacheCreationTokenCount: z.number(),
+	cacheReadTokenCount: z.number(),
 	modelCalls: z.number(),
 	toolCalls: z.number(),
 });
```

```bash
diff PKG/lib/8.10/agent-instance.ts PKG/lib/8.11/agent-instance.ts
```

## Procedure 4: Add a schema or an endpoint to a module

1. Do Procedure 1 and Procedure 2.
2. In each selected version tree, open `lib/<version>/<module>.ts`.
3. Add the schemas and the types. Use the table "Names" in [reference.md](reference.md).
4. Add the endpoint object. If the URL has parameters, give them to `Endpoint<...>`:

   ```ts
   const getAgentInstance = {
   	method: 'GET',
   	getUrl: ({agentInstanceKey}) => `/${API_VERSION}/agent-instances/${agentInstanceKey}` as const,
   } as const satisfies Endpoint<{agentInstanceKey: string}>;

   const queryAgentInstances = {
   	method: 'POST',
   	getUrl: () => `/${API_VERSION}/agent-instances/search` as const,
   } as const satisfies Endpoint;
   ```

5. Add the new values to the `export {…}` block. Add the new types to the `export type {…}` block.
6. Open `lib/<version>/index.ts`. Change it in three locations:
   1. Add the endpoint to the import list of the module.
   2. Add the endpoint to the `endpoints` object.
   3. Add the new schemas and types to the `export {…} from './<module>';` block.
7. Do not export the endpoint by name from `index.ts`. Consumers use `endpoints.<name>`.
8. Do Procedure 7 and Procedure 8.

## Procedure 5: Add a module

1. Make the file `lib/<version>/<module>.ts`. Copy the license header from a different module.
2. Do Procedure 4 for the new file.
3. In `PKG/vite.config.ts`, add an entry to `build.lib.entry`:

   ```ts
   '8.11/<module>': resolve(__dirname, 'lib/8.11/<module>.ts'),
   ```

4. In `PKG/package.json`, add an entry to `exports`:

   ```json
   "./8.11/<module>": {
   	"import": {
   		"types": "./dist/8.11/<module>.d.ts",
   		"default": "./dist/8.11/<module>.js"
   	}
   },
   ```

5. Do steps 1 to 4 for each selected version tree.
6. If two modules import schemas from each other, move the shared schemas to a helper module. Examples are `processes.ts` and `group-role.ts`.
7. Do not add helper modules to `exports`.
8. Do Procedure 7 and Procedure 8.

CAUTION: The build stops if it finds a circular import. The plugin `vite-plugin-circular-dependency` does this check.

## Procedure 6: Add a version tree

Use this procedure when the spec adds a change for a release line that has no version tree.
Commit `b923833bf06` is an example.

1. Copy the highest version tree to a new folder:

   ```bash
   cp -R PKG/lib/8.11 PKG/lib/8.12
   ```

2. In `PKG/vite.config.ts`, copy the entries of the highest tree. Change the version in the copies.
3. In `PKG/package.json`, copy the `exports` entries of the highest tree. Change the version in the copies.
4. Put the new change only in the new tree.
5. If a lower tree has the change, remove the change from the lower tree.
6. In the consumers that need the change, change the import to the new sub-path, for example `/8.12`.
7. Add the new version to the version lists in these files:
   - `docs/monorepo-docs/frontend/camunda-api-zod-schemas.md`
   - `docs/monorepo-docs/frontend/project-outline.md`
8. Do Procedure 7 and Procedure 8.

## Procedure 7: Increase the version

Do this procedure in the same PR as the schema change.

1. Find the current version in `PKG/package.json`.
2. Find the versions on npm:

   ```bash
   npm view @camunda/camunda-api-zod-schemas versions --json
   ```

3. If the current version is not on npm and `CHANGELOG.md` has a section for it, add your change to that section. Then go to step 7.
4. If the current version is on npm, increase the last number by one. For example, `0.0.93` becomes `0.0.94`.
5. Write the new version in these files:
   - `PKG/package.json` (`version`).
   - `webapp/client/apps/orchestration-cluster-webapp/package.json` (`dependencies`).
   - `webapp/client/packages/c8-mocks/package.json` (`devDependencies`).
6. In `webapp/client/`, use this command to change `package-lock.json`. Do not change the lockfile manually.

   ```bash
   npm i
   ```

7. Add a section at the top of `PKG/CHANGELOG.md`, below `# Changelog`. Use the changelog template in [reference.md](reference.md).
8. Do not change `operate/client` or `identity/client` in this PR. Refer to Procedure 9.

## Procedure 8: Examine the change

Do these steps in `webapp/client/`:

1. Build the package:

   ```bash
   npm run build -w @camunda/camunda-api-zod-schemas
   ```

2. Do the type check of the package:

   ```bash
   npm run typecheck -w @camunda/camunda-api-zod-schemas
   ```

3. Do the type check of all workspaces. This step examines the consumers:

   ```bash
   npm run typecheck
   ```

4. Format the package:

   ```bash
   npm run format -w @camunda/camunda-api-zod-schemas
   ```

5. Do the lint:

   ```bash
   npm run lint
   ```

6. If a command shows errors, find the cause and remove it.
7. If a consumer type error occurs, change the consumer code or the MSW mocks in the consumer.

NOTE: The package has no unit tests. The build, the type checks, and the lint are the only automatic checks.

## Procedure 9: Publish and change the legacy consumers

Do this procedure after the PR merges into `main`.

CAUTION: The publish workflow sends the package to the public npm registry. You cannot undo a publish. Do not start the workflow yourself.

1. Tell the user to start the workflow "Publish Zod Schemas to npm" (`.github/workflows/publish-zod-schemas.yml`).
2. Tell the user to start it from `main` and to set `dry_run` to false. The user can use this command:

   ```bash
   gh workflow run publish-zod-schemas.yml --repo camunda/camunda --ref main -f dry_run=false
   ```

3. After the workflow completes, make sure that the new version is on npm:

   ```bash
   npm view @camunda/camunda-api-zod-schemas version
   ```

4. Offer to make the follow-up PRs for `operate/client` and `identity/client`.
5. If the user agrees, do these steps in each folder:
   1. In `package.json`, change `@camunda/camunda-api-zod-schemas` to the new version.
   2. Use `npm i` to change the `package-lock.json` of that folder.
   3. Use `npm run lint` to do the type check and the lint.

NOTE: The `.npmrc` files contain `min-release-age=1`. If `npm i` cannot find the new version, wait one day. Then do the step again.

## Examples

| Change                   | Reference     | Files                                                                                                                  |
| ------------------------ | ------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Field change and version | PR #62253     | `lib/8.10/agent-instance.ts`, `PKG/package.json`, `CHANGELOG.md`, 2 consumer `package.json` files, `package-lock.json` |
| New version tree         | `b923833bf06` | `lib/8.11/*`, `vite.config.ts`, `PKG/package.json` exports, consumer imports                                           |
| Version increase only    | `6b40fcd592f` | `PKG/package.json`, `CHANGELOG.md`, 2 consumer `package.json` files, `package-lock.json`                               |
