# Reference: Camunda API Zod schemas

This file contains the tables and templates for the skill [camunda-api-zod-schemas](SKILL.md).
`PKG` is `webapp/client/packages/camunda-api-zod-schemas/`.

## Spec to Zod

| Spec                                               | Zod                                                          |
| -------------------------------------------------- | ------------------------------------------------------------ |
| Property in `required`, with `nullable: true`      | `.nullable()`                                                |
| Property not in `required`                         | `.optional()`                                                |
| `type: integer` or `type: number`                  | `z.number()`                                                 |
| `type: string` (also `format: date-time` and keys) | `z.string()`                                                 |
| `*Enum` schema                                     | `z.enum([...])` with the same upper-case values              |
| `oneOf` with a `discriminator`                     | `z.discriminatedUnion('<property>', [...])` with `z.literal` |
| Object with `additionalProperties`                 | `z.record(z.string(), z.unknown())`                          |
| Any JSON value                                     | `z.unknown()`                                                |
| `BasicStringFilterProperty`, `*KeyFilterProperty`  | `basicStringFilterSchema`                                    |
| `StringFilterProperty` (has `$like`)               | `advancedStringFilterSchema`                                 |
| `IntegerFilterProperty`                            | `advancedIntegerFilterSchema`                                |
| `DateTimeFilterProperty`                           | `advancedDateTimeFilterSchema`                               |
| `<Enum>FilterProperty`                             | `getEnumFilterSchema(<enumSchema>)`                          |
| Filter with `$or`                                  | `getOrFilterSchema(<filterSchema>)`                          |
| Field enum of `<Entity>SearchQuerySortRequest`     | `sortFields` in `getQueryRequestBodySchema({...})`           |
| Search response with `items` and `page`            | `getQueryResponseBodySchema(<itemSchema>)`                   |
| Response with only `items`                         | `getCollectionResponseBodySchema(<itemSchema>)`              |

Put the filter properties in `z.object({...}).partial()`.

NOTE: Some modules use a different filter helper than the table shows. Keep the helper that the module uses for current properties.

NOTE: Tree 8.8 uses `.optional()` for many nullable properties. Keep the style of the tree that you change.

## Helpers in `common.ts`

| Helper                                            | Result                                                                     |
| ------------------------------------------------- | -------------------------------------------------------------------------- |
| `API_VERSION`                                     | `'v2'`. Use it in all endpoint URLs                                        |
| `basicStringFilterSchema`                         | A string, or `$eq`, `$neq`, `$exists`, `$in`, `$notIn`                     |
| `advancedStringFilterSchema`                      | The basic string filter and `$like`                                        |
| `advancedIntegerFilterSchema`                     | A number, or `$eq`, `$neq`, `$exists`, `$gt`, `$gte`, `$lt`, `$lte`, `$in` |
| `advancedDateTimeFilterSchema`                    | A date-time string, or the same operators as the integer filter            |
| `getEnumFilterSchema(zEnum)`                      | An enum value, or an operator object for the enum                          |
| `getOrFilterSchema(objSchema)`                    | The filter object and a `$or` array of filter objects                      |
| `getQueryRequestSortSchema(fields)`               | An array of `{field, order?: 'asc' \| 'desc'}`                             |
| `getQueryRequestBodySchema({sortFields, filter})` | A partial object with `sort`, `page`, and `filter`                         |
| `getQueryResponseBodySchema(item)`                | An object with `items` and `page`                                          |
| `getCollectionResponseBodySchema(item)`           | An object with `items`                                                     |
| `Endpoint<URLParams>`                             | The type of an endpoint object: `method` and `getUrl`                      |

NOTE: The helpers are different in each version tree. Read the `common.ts` that the module imports before you use a helper.

Example of a search request body (the list of sort fields is shorter than in the module):

```ts
const queryAgentInstancesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['agentInstanceKey', 'status', 'creationDate'] as const,
	filter: agentInstanceFilterSchema,
});
```

## Names

| Item            | Schema or value name                      | Type name                         |
| --------------- | ----------------------------------------- | --------------------------------- |
| Entity          | `agentInstanceSchema`                     | `AgentInstance`                   |
| Enum            | `agentInstanceStatusSchema`               | `AgentInstanceStatus`             |
| Filter          | `agentInstanceFilterSchema`               | `AgentInstanceFilter`             |
| Search request  | `queryAgentInstancesRequestBodySchema`    | `QueryAgentInstancesRequestBody`  |
| Search response | `queryAgentInstancesResponseBodySchema`   | `QueryAgentInstancesResponseBody` |
| Get response    | `getAgentInstanceResponseBodySchema`      | `GetAgentInstanceResponseBody`    |
| Command request | `create<Entity>RequestBodySchema`         | `Create<Entity>RequestBody`       |
| Endpoint        | `getAgentInstance`, `queryAgentInstances` | Not applicable                    |

A get response schema is the same object as the entity schema:

```ts
const getAgentInstanceResponseBodySchema = agentInstanceSchema;
type GetAgentInstanceResponseBody = z.infer<typeof getAgentInstanceResponseBodySchema>;
```

NOTE: Older modules use `query*` for search endpoints. Newer modules, for example `cluster-variable.ts`, use `search*`. Use the prefix of the module.

## Changelog template

```markdown
## v0.0.94

### 🚀 Enhancements

- Add `newField` to the 8.10 and 8.11 agent instance schemas [#12345](https://github.com/camunda/camunda/issues/12345)

### ❤️ Contributors

- Full Name ([@github-user](https://github.com/github-user))
```

| Heading                   | Use it for                                                             |
| ------------------------- | ---------------------------------------------------------------------- |
| `### 🚀 Enhancements`     | New fields, enum values, schemas, endpoints, modules, or version trees |
| `### 🩹 Fixes`            | Changes that make a schema agree with the spec                         |
| `### ⚠️ Breaking Changes` | Removed or renamed fields, narrower types, or removed endpoints        |
| `### ❤️ Contributors`     | All releases. Write the name and the GitHub user of each author        |

Rules for each bullet:

- Put names of fields, enums, and schemas in backticks.
- Write the version trees, for example "the 8.10 and 8.11 agent instance schemas".
- Put the issue link at the end.

To find the author, use this command:

```bash
gh api user --jq '.name + " (@" + .login + ")"'
```

## Files that each change type touches

| Change type                      | Files                                                                                                                                                     |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Field or enum                    | `lib/<version>/<module>.ts`                                                                                                                               |
| Schema or endpoint               | `lib/<version>/<module>.ts`, `lib/<version>/index.ts`                                                                                                     |
| Module                           | The files above, `vite.config.ts`, `PKG/package.json` (`exports`)                                                                                         |
| Version tree                     | `lib/<new-version>/*`, `vite.config.ts`, `PKG/package.json` (`exports`), consumer imports, two docs files                                                 |
| Version increase                 | `PKG/package.json`, `CHANGELOG.md`, `apps/orchestration-cluster-webapp/package.json`, `packages/c8-mocks/package.json`, `webapp/client/package-lock.json` |
| Legacy consumers (after publish) | `operate/client/package.json` and lockfile, `identity/client/package.json` and lockfile                                                                   |
