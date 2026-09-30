# Reference: Camunda API Zod schemas

`PKG` is `webapp/client/packages/camunda-api-zod-schemas/`.

## Scripts

| Command (in `PKG`)                             | Result                                                                                  |
| ---------------------------------------------- | --------------------------------------------------------------------------------------- |
| `npm run download-specs -- [--local] [-v <v>]` | Gets the spec to `specs/<v>/`. `--local` copies the current version from the repository |
| `npm run generate-schemas -- [-v <v>]`         | Preprocesses the spec, then Kubb writes `lib/<v>/gen/`                                  |
| `npm run clean`                                | Removes `specs/` and all `gen/` folders                                                 |
| `npm run prepare`                              | Copies, generates, and builds the current version. `npm ci` starts it                   |

`-v current` selects the release line in `webapp/client/package.json`. `CONFIG` in `scripts/supported-versions.js` contains the versions.

## Preprocessing

`scripts/preprocess-spec.js` changes a copy of the spec before Kubb reads it. It corrects Kubb errors.
It does not change the meaning of the spec.
If the gen output is not correct and the spec is correct, add a patch to `PATCHES` with a `reason`.

## Generated trees

- Import from `./gen/zod/<name>Schema` and `./gen/types/<Name>`. Do not import from `./gen`, because it adds all schemas to `dist/`.
- Take types from `gen/types`, not from `z.infer`. With `.default()`, `z.infer` makes fields required.
- Give the public name to the gen schema: `const userSchema = userResultSchema; type User = UserResult;`.
- For request bodies, use the component schema (`userTaskCompletionRequestSchema`), not the operation body schema.
- For responses, use `<Operation>Status200` or the component schema. `<Operation>Response` also contains `ProblemDetail`.
- For an inline enum, use the parent shape, for example `partitionSchema.shape.role`.
- Search query schemas are intersections and have no `.shape`. Use the separate sort or filter schema.
- If a gen name is the same as a public name, rename the import: `jobResultSchema as genJobResultSchema`.
- Integers are `number`. The sort order is `'ASC' | 'DESC'`.

To find a gen name, use `ls PKG/lib/8.11/gen/zod | grep -i <entity>`.

## Manual trees

- Put `type X = z.infer<typeof xSchema>;` immediately after each schema.
- A property in `required` with `nullable: true` gets `.nullable()`. A property not in `required` gets `.optional()`.
- Use the filter and query helpers in the `common.ts` that the module imports. Do not change that import.
- The sort order is `'asc' | 'desc'`.
- Make the change the same in all selected manual trees. Use `diff` to compare them.

## Pods

Use the first row that agrees with the path of the changed file in `apps/orchestration-cluster-webapp/`.

| Path contains | Pod                                      |
| ------------- | ---------------------------------------- |
| `tasklist`    | `@camunda/employee-engagement-tasklist`  |
| `operate`     | `@camunda/operate-admin-pod`             |
| `admin`       | `@camunda/operate-admin-pod`             |
| Other         | `@camunda/orchestration-cluster-webapps` |

## Changelog template

```markdown
## v0.0.95

### ⚠️ Breaking Changes

- Make `processDefinitionName` nullable in the 8.11 process instance schemas, as in the spec [#12345](https://github.com/camunda/camunda/issues/12345)

### 🚀 Enhancements

- Add `newField` to the 8.10 and 8.11 agent instance schemas [#12345](https://github.com/camunda/camunda/issues/12345)

### ❤️ Contributors

- Full Name ([@github-user](https://github.com/github-user))
```

Use `### 🩹 Fixes` for changes that make a schema agree with the spec. To find the author, use `gh api user --jq '.name + " (@" + .login + ")"'`.
