# C8 REST API surface snapshot

CI check that makes every change to the C8 REST API contract visible, and
reviewable, in the PR that makes it.

[`api-surface.mjs`](api-surface.mjs) bundles the spec under
[`zeebe/gateway-protocol/src/main/proto/v2/`](../../../zeebe/gateway-protocol/src/main/proto/v2/)
with `camunda-schema-bundler` (the same bundler and options the SDKs use).
It renders the bundle as a line-oriented snapshot, committed at
[`zeebe/gateway-protocol/rest-api-surface.txt`](../../../zeebe/gateway-protocol/rest-api-surface.txt).

The `openapi-api-surface-check` job in [`ci.yml`](../../workflows/ci.yml)
regenerates the snapshot and fails if it differs from the committed file. A PR
that changes the API surface therefore has to commit the regenerated snapshot,
and the diff of that file is the API change.

## Why

The orchestration cluster SDKs (TypeScript, Python, C#, Go, Java, PHP, Rust)
are generated from this spec. Until now, contract changes were found when an
SDK's generation or tests broke, days after the spec PR merged, sometimes on a
stable branch and after a patch release. Examples from 8.10:

| Spec change | Wire-breaking? | SDK-breaking? | Snapshot diff |
|---|---|---|---|
| Legacy `processDefinitionKey`/`Id` made nullable (a fix) | yes | yes (C# public API) | 7 `PROP … required` → `required nullable` lines |
| Process-creation fields moved into an `allOf` base (refactor) | no | yes (Python lost `…ByKeyVariables`, Java/PHP lost semantic types) | new base schema, 3 `SCHEMA … allOf=[…]` lines, `closed` dropped, `variables` inline owner moved |
| Create by version tag (feature) | no (additive) | yes (Rust exhaustive `match`) | third `oneOf` variant and its properties |

The wire-level breaking-change check (oasdiff, #66629) answers a different
question: does this break existing API callers? Of these three, only the first
is a wire break. The other two are wire-compatible, so that check correctly
lets them through, yet they broke four SDKs. Every wire break also shows up in
the snapshot, but not the other way round. The two checks complement each
other.

## What the snapshot records

One line per operation, schema and property. Properties are flattened through
`allOf`, so a pure restructuring changes only the `SCHEMA` lines.

```
OP     POST /process-instances  createProcessInstance  params=[] req=application/json:ProcessInstanceCreationInstruction  200=CreateProcessInstanceResult 400=ProblemDetail …
SCHEMA ProcessInstanceCreationInstructionById  object allOf=[ProcessInstanceCreationInstructionBase] not={"required": ["processDefinitionVersionTag"]}
PROP   ProcessInstanceCreationInstructionById.tenantId  TenantId  optional  semantic=TenantId
PROP   ProcessInstanceCreationInstructionById.variables  object(inline@ProcessInstanceCreationInstructionBase.variables)  optional
```

- **Wire contract:** operations, parameters, request and response schemas,
  response codes, property types, `required`, `nullable`, enum values, `oneOf`
  variants, closed objects (`additionalProperties: false`), `not` constraints.
- **Generated-code contract:** component schema names, `allOf` structure,
  `x-semantic-type`, and which schema owns an inline object. Generators name
  inline classes after their owner.

Descriptions, examples and `x-added-in-version` are deliberately left out, so
documentation-only changes never touch the snapshot.

## Reviewing a snapshot diff

Classify each changed line:

| Change | Kind | Typical lines |
|---|---|---|
| New operation, schema, optional request property, response property, enum value in a request | additive | `+OP`, `+SCHEMA`, `+PROP … optional` |
| Spec corrected to match what the server already does (e.g. a field the server can return as `null`) | **fix**: breaking for SDK types but not a behaviour change; say so in the PR description | `PROP … nullable` added |
| Removed or renamed operation, schema or property; type change; newly required request property; new response enum value or `oneOf` variant | **breaking** | `-OP`, `-SCHEMA`, `-PROP`, changed type |
| Restructuring with identical wire format (`allOf` base, renamed component, moved inline object) | **refactor**: wire-neutral, but it changes generated SDK code | `SCHEMA … allOf=[…]`, `inline@…` owner |

Guidance:

- On `main`, all of these are possible. Breaking and refactor changes need a
  deliberate decision from the API owners and the SDK team.
- On `stable/*`, only additive changes and **fixes** should be backported.
  Refactors in particular belong on `main` only: they are wire-neutral but
  break every SDK that regenerates from the stable branch.

## Running locally

```bash
cd .github/scripts/api-surface
npm ci
npm run generate   # rewrite zeebe/gateway-protocol/rest-api-surface.txt
npm run check      # what CI runs: fails and prints the diff if the snapshot is stale
node api-surface.mjs --spec path/to/bundled.json   # surface of any bundled spec, to stdout
```

## Env

| Var | Default | Purpose |
|---|---|---|
| `OCA_SPEC_PATH` | `zeebe/gateway-protocol/src/main/proto/v2` | Spec directory to bundle |
| `API_SURFACE_PATH` | `zeebe/gateway-protocol/rest-api-surface.txt` | Snapshot file |
