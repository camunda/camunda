---
architecture_md: 1
component: camunda/camunda/zeebe/gateway-protocol
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The API contracts of the Orchestration Cluster, the OpenAPI spec of the REST API v2 with its x-* annotations and the Zeebe gRPC API (gateway.proto), plus the Spectral rules that enforce the spec conventions in CI.
team: { name: camunda/c8-api-team, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/c8-api] }
owns:
  - "The Orchestration Cluster REST API v2 contract in src/main/proto/v2/: paths, operationIds, parameters, request and response schemas, enums, response codes and per-operation security declarations"
  - "The operation and schema annotation vocabulary and its values: x-added-in-version, x-properties-added-in-version, x-added-in-version-override, x-scope, x-eventually-consistent, x-required-permissions, x-permission-enforcement, x-semantic-establishes/-requires/-provider/-client-minted, x-present-when, x-deprecated-enum-members, x-polymorphic-schema, x-semantic-type"
  - "The registries the annotations refer to: v2/semantic-kinds.json (semantic kinds and their shape) and v2/resource-permissions.json (valid resourceType/permissionType pairs, mirrored from the security model)"
  - "The Zeebe gRPC API definition src/main/proto/gateway.proto (package gateway_protocol): services, RPCs, messages and field numbers"
  - "The Spectral ruleset (.spectral.yaml), the custom rule functions (spectral-functions/) and their fixture tests (spectral-tests/), and the validation guide OPENAPI_VALIDATION.md"
  - "The io.camunda:zeebe-gateway-protocol artifact, which packages *.proto and v2/*.yaml as classpath resources"
does_not_own:
  - { concept: "REST controllers, routing, serving the OpenAPI document and Swagger UI, runtime response validation", owner: camunda/camunda/zeebe/gateway-rest }
  - { concept: "Java DTOs generated from the spec (advanced and simple models) and the generator configuration", owner: camunda/camunda/gateways/gateway-model }
  - { concept: "Request validation, request/response mapping and error-to-ProblemDetail mapping", owner: camunda/camunda/gateways/gateway-mapping-http }
  - { concept: "gRPC Java stubs generated from gateway.proto", owner: camunda/camunda/zeebe/gateway-protocol-impl }
  - { concept: "The gRPC server, job long-polling and job streaming behavior", owner: camunda/camunda/zeebe/gateway-grpc }
  - { concept: "What a command does, its rejections and write authorization", owner: camunda/camunda/zeebe/engine }
  - { concept: "The permission model (AuthorizationResourceType, PermissionType) that resource-permissions.json mirrors", owner: camunda/camunda/security }
  - { concept: "Engine record format (SBE records, record value types)", owner: camunda/camunda/zeebe/protocol }
  - { concept: "Java client commands and the client's generated REST model", owner: camunda/camunda/clients }
  - { concept: "MCP tool schemas (simplified through the gateway-model 'simple' generation, never by editing the spec)", owner: camunda/camunda/gateways/gateway-mcp }
  - { concept: "@camunda/camunda-api-zod-schemas, the hand-written TypeScript schemas the webapps use", owner: camunda/camunda/webapp/client }
  - { concept: "Generated request-validation tests and response-shape assertions", owner: camunda/camunda/qa/c8-orchestration-cluster-e2e-test-suite }
  - { concept: "Rendering the public REST API reference from the spec", owner: camunda/camunda-docs }
depends_on:
  - id: security-model
    component: camunda/camunda/security
    kind: schema
    contract: "AuthorizationResourceType.buildResourcePermissionsMap() (security/security-protocol), mirrored by v2/resource-permissions.json and checked by ResourcePermissionsRegistryTest in zeebe/gateway-rest"
    versions: same monorepo release
    workaround_policy: never
  - id: spectral
    component: "camunda/camunda-spectral (@camunda8/spectral-cli, Camunda's fork of Stoplight Spectral)"
    kind: external
    contract: "spectral lint with .spectral.yaml (extends spectral:oas recommended) and custom functions; also needed at runtime by spectral-tests/helpers.js"
    versions: "pinned as SPECTRAL_VERSION in the openapi-lint job of .github/workflows/ci.yml (Renovate-managed)"
    workaround_policy: adapter-boundary
  - id: buf
    component: "Buf (bufbuild/buf-action)"
    kind: external
    contract: "buf breaking with the FILE rule set for src/main/proto, configured in the repo-root buf.yaml"
    versions: "action pinned by SHA in the protobuf-checks job of .github/workflows/ci.yml"
    workaround_policy: adapter-boundary
  - id: openapi-proto-specs
    component: "OpenAPI 3.0.3 and Protocol Buffers proto3"
    kind: external
    contract: "The spec formats the files are written in; generators downstream assume OpenAPI 3.0.x"
    versions: "OpenAPI 3.0.3; proto3"
    workaround_policy: never
consumers:
  - { who: "API users: applications, job workers, scripts and SDKs in any language calling the cluster over HTTP", via: "REST API v2 as specified in v2/rest-api.yaml", promise: "forward-compatible between minor versions; breaking changes only deliberately and communicated; deprecation before removal (major, or at least two minors); alpha endpoints and properties exempt" }
  - { who: "gRPC client users who generate their own stubs (any language; go_package is set)", via: gateway.proto, promise: "source-compatible at FILE level (buf breaking on every .proto change) unless the PR declares BREAKING CHANGE; stub generation is not a derivative work under the Camunda License 1.0" }
  - { who: "camunda/camunda/zeebe/gateway-protocol-impl, and through it zeebe/gateway, zeebe/gateway-grpc and clients/java", via: "gateway.proto compiled into Java stubs at build time", promise: "same monorepo release" }
  - { who: camunda/camunda/gateways/gateway-model, via: "v2/*.yaml read from the source tree at build time (openapi-generator 'spring' simple model, custom advanced-model generator)", promise: "same monorepo release; a spec change regenerates the DTOs" }
  - { who: "camunda/camunda/clients (Java client)", via: "v2/rest-api.yaml read at build time (openapi-generator 'java' into io.camunda.client.protocol.rest, discriminator post-processor)", promise: "same monorepo release; client and Spring Boot starter compatibility tested in CI" }
  - { who: camunda/camunda/zeebe/gateway-rest, via: "the spec on the classpath (io.camunda:zeebe-gateway-protocol) for the served OpenAPI document and response validation; spec-sync and registry tests", promise: "same monorepo release; controllers must match the spec (guidelines § 2.16)" }
  - { who: camunda/camunda/dist, via: "v2/*.yaml copied into the distribution under config/openapi/v2", promise: "same monorepo release" }
  - { who: "camunda/camunda/gateways/gateway-mcp", via: "simple models generated from the spec; x-eventually-consistent read by convention for tool descriptions", promise: "same monorepo release" }
  - { who: "camunda/camunda/webapp/client (@camunda/camunda-api-zod-schemas, published to npm)", via: "hand-written from v2/*.yaml", promise: "none from this component; the package follows the spec by hand" }
  - { who: "camunda/camunda/qa/c8-orchestration-cluster-e2e-test-suite", via: "request-validation test generator, generated API paths, assert-json-body response checks against v2/*.yaml", promise: "same branch; generated tests are regenerated, never edited" }
  - { who: "CI checks: openapi-lint, openapi-x-added-in-version-check (.github/scripts/x-added-in-version-check), protobuf-checks", via: "v2/*.yaml, .spectral.yaml, spectral-functions, gateway.proto", promise: none }
  - { who: "camunda/camunda-docs (public REST API reference)", via: "weekly sync of v2 spec from main and every supported stable/* branch; alpha-sentinel and x-scope transforms", promise: "spec descriptions are the docs; the alpha sentinel text is matched literally" }
  - { who: "camunda/api-test-generator", via: "x-semantic-*, x-required-permissions, x-permission-enforcement and semantic-kinds.json", promise: "annotation shapes as documented in guidelines § 2.18–2.19; TODO(confirm) versioning of the vocabulary" }
  - { who: "SDK generators that derive typing from x-present-when and the spec (TODO(confirm) which SDK repos)", via: "v2/*.yaml", promise: "as API users; x-present-when derivation contract in guidelines § 2.21" }
  - { who: "Other Camunda products (Web Modeler, Desktop Modeler, Console, Connectors) and Camunda Process Test", via: "REST API v2 and gRPC, through the clients or directly", promise: as API users }
exposes:
  - { contract: "Orchestration Cluster REST API v2 specification (entry point v2/rest-api.yaml, per-domain files, shared models)", spec: src/main/proto/v2/rest-api.yaml, policy: "forward-compatible between minors (guidelines § 2.7); deprecate before removal; alpha marked with the exact sentinel (§ 2.8); every operation carries x-added-in-version, x-scope, x-required-permissions and security" }
  - { contract: "Annotation vocabulary (x-*) for docs, test generators, SDK generators and code generators", spec: ../../docs/rest-api-endpoint-guidelines.md, policy: "shapes enforced by Spectral rules; meaning documented in guidelines § 2.4–2.21; TODO(confirm) whether consumers get a compatibility promise when a shape changes" }
  - { contract: "Registries semantic-kinds.json and resource-permissions.json", spec: src/main/proto/v2/semantic-kinds.json, policy: "source-tree only (not packaged in the jar); a new kind ships with a consumer and, for entity/edge kinds, a producer; resource-permissions.json mirrors the security model exactly" }
  - { contract: "Zeebe gRPC API (gateway_protocol.Gateway service and messages)", spec: src/main/proto/gateway.proto, policy: "buf breaking (FILE) on every PR touching .proto, skipped only with BREAKING CHANGE: in the PR body or merge commit; kept for compatibility and job streaming; new core features go to REST first" }
  - { contract: "Maven artifact io.camunda:zeebe-gateway-protocol (resources only: *.proto, v2/*.yaml)", spec: pom.xml, policy: "versioned with the monorepo; language level 8; not in the BOM (zeebe-gateway-protocol-impl is)" }
  - { contract: "Extension point: Spectral ruleset and custom functions; any team can add a rule for a new spec convention", spec: .spectral.yaml, policy: "each rule ships with fixtures and node --test tests in spectral-tests/; existing violations are grandfathered by an allowlist, not by weakening the rule" }
constraints:
  - { id: C1, name: Design review and API team sign-off, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow" }
  - { id: C2, name: REST forward compatibility between minors, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#27-backwards-compatibility-and-breaking-changes" }
  - { id: C3, name: gRPC source compatibility, hard: true, ref: ../../buf.yaml }
  - { id: C4, name: Complete and correct operation annotations, hard: true, ref: OPENAPI_VALIDATION.md }
  - { id: C5, name: Naming and schema conventions, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#22-naming-and-terminology" }
  - { id: C6, name: Alpha and deprecation marking, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#28-alpha-endpoints-and-properties" }
  - { id: C7, name: Generated and hand-written consumers follow the contract, hard: false, ref: "../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow" }
  - { id: C8, name: Spectral rule changes are tested and grandfathered, hard: true, ref: spectral-tests/README.md }
decisions: ../../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/zeebe/gateway-protocol

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

This module holds the contracts that callers of the Orchestration Cluster code against: the
OpenAPI spec of the REST API v2 (`src/main/proto/v2/`) and the Zeebe gRPC API
(`src/main/proto/gateway.proto`). It also holds the Spectral rules that check the spec's
conventions in CI. It has no Java code. Code generators, the REST gateway, the Java client, the
docs site and test generators all read these files. It is part of the
[Orchestration Cluster system](../../SYSTEM.md) (role `contracts`).

## 2. Ownership boundary

**Owns:** the shape of every REST operation and gRPC RPC: what a caller sends, what it gets back,
which codes it can see, and the metadata on each operation (version, scope, consistency, required
permissions, semantic graph). It also owns the machine checks for these rules. The full list is in
the front matter. It does not own behavior: what an operation does lives in `service/` and the
engine. How it is served lives in `zeebe/gateway-rest` and `zeebe/gateway-grpc`.

Owner: GitHub [CODEOWNERS](../../CODEOWNERS) has a line only for `src/main/proto/v2/`
(`@camunda/c8-api-team`, "C8 REST OpenAPI specification"), so the front matter uses that team. It has
no line for `gateway.proto`, `.spectral.yaml`, `spectral-functions/` or `spectral-tests/`. The
fine-grained [`.codeowners`](../../.codeowners) file (used to attribute failing tests) gives the whole
directory to `@camunda/core-features`. The [endpoint guidelines § 1](../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow)
say feature teams write spec changes and `@camunda/c8-api-team` reviews them.
TODO(confirm): who owns `gateway.proto` and the Spectral rules (c8-api-team or core-features), and
whether one CODEOWNERS line should cover the whole directory. Contact channel: TODO(confirm);
endpoint designs go to [`#top-c8-cluster-api-governance`](https://camunda.slack.com/archives/C0A154VV8DB).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A controller, route, `@RequiresSecondaryStorage`, or the served Swagger UI | `camunda/camunda/zeebe/gateway-rest` | issue, `component/c8-api` |
| A different generated DTO without changing the contract | `camunda/camunda/gateways/gateway-model` (generator config) | issue, `component/gateway` |
| A simpler MCP tool schema | `gateways/gateway-mcp` via the `simple` execution in `gateways/gateway-model/pom.xml`; never by editing the spec | issue, `component/mcp` |
| gRPC stubs, gRPC server behavior, job streaming | `camunda/camunda/zeebe/gateway-protocol-impl`, `zeebe/gateway-grpc`, `zeebe/gateway` | issue |
| A new permission or resource type | `camunda/camunda/security` first; then `resource-permissions.json` here | issue |
| What a command does, or a new rejection | `camunda/camunda/zeebe/engine` | issue, `component/zeebe-engine` |
| A Java client command | `camunda/camunda/clients` (`@camunda/c8-api-team`) | issue, `component/clients` |
| Zod schemas for the webapps | `camunda/camunda/webapp/client` (`camunda-api-zod-schemas` skill) | issue |
| Generated request-validation tests | `qa/c8-orchestration-cluster-e2e-test-suite` (edit the generator, re-run) | issue |
| Docs page layout beyond what the spec gives | `camunda/camunda-docs` | docs PR |
| A change to the API guidelines themselves | [`docs/rest-api-endpoint-guidelines.md`](../../docs/rest-api-endpoint-guidelines.md) (`@camunda/c8-api-team`) | issue, `api-proposal.md` |

## 3. Structure

| Path | What it holds |
|---|---|
| `src/main/proto/v2/rest-api.yaml` | REST entry point: info, servers, security schemes, tags, path `$ref`s |
| `src/main/proto/v2/<domain>.yaml` | Per-domain paths and schemas (`process-instances.yaml`, `jobs.yaml`, `user-tasks.yaml`, …; about 40 domains) |
| `src/main/proto/v2/{common-responses,problem-detail,search-models,filters,keys,identifiers,cursors}.yaml` | Shared models: responses, RFC 9457 ProblemDetail, pagination and sort, advanced filters, key and identifier types |
| `src/main/proto/v2/semantic-kinds.json`, `resource-permissions.json` | Registries for `x-semantic-*` and `x-required-permissions` |
| `src/main/proto/gateway.proto` | Zeebe gRPC API (`java_package io.camunda.zeebe.gateway.protocol`) |
| `src/main/proto/rest-api.yaml` | One-line relocation note pointing at `v2/rest-api.yaml` |
| `src/main/proto/rest-api-v1.yaml` | Legacy "Zeebe REST API" v1 (version 0.1, `/v1`). Nothing in the repo references it and the jar doesn't package it. TODO(confirm): dead, or kept for a consumer? |
| `.spectral.yaml`, `spectral-functions/`, `spectral-tests/` | Ruleset, custom JS functions, fixture-based tests |
| `OPENAPI_VALIDATION.md` | How to run the linter and what each rule catches |
| `pom.xml` | Packages `**/*.proto` and `v2/*.yaml` as resources; no dependencies |

Direction rules:

- Contracts depend on nothing else in the system. Generators read the files in place: `gateway-model`,
  `clients/java` and `gateway-protocol-impl` use `${maven.multiModuleProjectDirectory}/zeebe/gateway-protocol/...`,
  and `dist` copies `v2/*.yaml`. `gateway-rest` uses the jar. Generated code is never edited; fix the
  spec or the generator ([overview § Path rules](../../docs/architecture/overview.md#path-rules)).
- A domain file `$ref`s shared files, never another domain's internals
  ([guidelines § 2.6](../../docs/rest-api-endpoint-guidelines.md#26-component-reuse-and-schema-organisation)).
  A new domain adds its file and its path `$ref`s in `rest-api.yaml` (§ 2.1).
- `resource-permissions.json` follows `security`, never the other way round.
- Spectral runs twice: a structural pass on `rest-api.yaml` (resolved) and a per-file pass on
  `v2/*.yaml`. Some rules fire only in the second pass, so local runs must do both.

Variant-specific content: SaaS vs Self-Managed differences are expressed per operation through
`security` (`bearerAuth`, `basicAuth`, or `[]`). Physical-tenant vs cluster-wide is `x-scope`.

## 4. Binding decisions

ADR index: [`docs/adr/`](../../docs/adr/README.md); Zeebe ADRs in [`zeebe/docs/adr/`](../docs/adr/README.md).
This module has no ADR folder of its own. The decisions that most often shape a contract change:

- [Endpoint required-permission mapping](../../docs/adr/security/001-endpoint-required-permission-mapping.md)
  (`x-required-permissions` and `x-permission-enforcement` live in this spec; status Proposed).
- [Physical-tenant management endpoint inventory](../../docs/adr/management/003-physical-tenant-management-endpoint-inventory.md)
  and [request scoping](../../docs/adr/orchestration-cluster/0003-physical-tenant-request-scoping-via-pre-security-filter.md)
  (`x-scope`, `/cluster/v2`).
- [Removing numeric keys from identity entity filters](../../docs/adr/storage/001-remove-numeric-key-from-identity-entity-filters.md).
- Zeebe [job lease](../docs/adr/0005-810-job-lease.md) (`withLease` in both APIs: over gRPC, older
  servers silently drop the unknown field, while REST rejects it with 400) and
  [physical-tenant job streaming](../docs/adr/0004-810-physical-tenant-job-streaming.md) (gRPC header).

Rules without an ADR are in the [REST API endpoint guidelines](../../docs/rest-api-endpoint-guidelines.md)
(§ 2 spec rules, § 3 Spectral) and the [architecture overview](../../docs/architecture/overview.md)
("REST API first"; gRPC protocol as a boundary that must not be bypassed).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Design review and API team sign-off
- **Question:** Was the endpoint contract (method, path, request/response shapes, codes) shared in
  `#top-c8-cluster-api-governance` before the spec PR? Who from `@camunda/c8-api-team` reviews it? Does
  the change touch `required` on an existing field, which needs their explicit sign-off?
- **Hard:** yes
- **Detail:** [Guidelines § 1, § 2.7 rule 1](../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow)

### C2 — REST forward compatibility between minors
- **Question:** Does the change remove or rename a schema, property, enum value or endpoint, change
  `required`/`nullable` or a type, add an enum value, harden a type, or upgrade a filter to advanced
  search? If it is breaking or SDK-breaking, how is it deprecated first, who communicates it, and
  which older-branch forward-compatibility tests change? For a spec refactor, how do you show that
  the resolved spec is semantically identical?
- **Hard:** yes
- **Detail:** [Guidelines § 2.7, § 2.12, § 2.13](../../docs/rest-api-endpoint-guidelines.md#27-backwards-compatibility-and-breaking-changes),
  forward-compatibility nightly (§ 7.6)

### C3 — gRPC source compatibility
- **Question:** Does the change touch `gateway.proto`? Is it additive (new field numbers, new RPCs,
  new messages)? Does `buf breaking` (FILE) pass, or does the PR need `BREAKING CHANGE:`, and who
  approves that? Does the same feature need a REST counterpart, given that REST comes first?
- **Hard:** yes
- **Detail:** [`buf.yaml`](../../buf.yaml), `protobuf-checks` job in `.github/workflows/ci.yml`,
  [overview § Architectural boundaries](../../docs/architecture/overview.md#architectural-boundaries)

### C4 — Complete and correct operation annotations
- **Question:** Does every new operation declare `x-added-in-version` (the version it first ships in),
  `x-scope`, `x-eventually-consistent` (true only for queries), `x-required-permissions` (or `[]`),
  `security`, and, where needed, `x-semantic-establishes`/`-requires` and `x-permission-enforcement`?
  Do new properties need `x-properties-added-in-version`? Does a new permission pair exist in
  `resource-permissions.json` and in `security`?
- **Hard:** yes
- **Detail:** [OPENAPI_VALIDATION.md](OPENAPI_VALIDATION.md), guidelines § 2.5, § 2.17–2.20,
  [`x-added-in-version-check`](../../.github/scripts/x-added-in-version-check/README.md)

### C5 — Naming and schema conventions
- **Question:** Are keys strings, identifiers qualified (no bare `id`/`key`), properties described,
  "element" used instead of "flow node", request and response schemas separate where requiredness
  differs, and response arrays required and non-nullable?
- **Hard:** yes
- **Detail:** [Guidelines § 2.2–2.6, § 10](../../docs/rest-api-endpoint-guidelines.md#22-naming-and-terminology)

### C6 — Alpha and deprecation marking
- **Question:** Is anything released as alpha, and does it use the exact two-line sentinel so the
  docs render the banner? Is anything deprecated (`deprecated: true`, `x-deprecated-enum-members`)
  with a removal version at least two minors out?
- **Hard:** yes
- **Detail:** [Guidelines § 2.8, § 9.3](../../docs/rest-api-endpoint-guidelines.md#28-alpha-endpoints-and-properties)

### C7 — Generated and hand-written consumers follow the contract
- **Question:** Who updates the controller (`zeebe/gateway-rest`), the Java client command, the zod
  schemas, the MCP tool (if any), the E2E API tests, and the gRPC callers for a `.proto` change? In
  which release? Does the docs sync need a manual run?
- **Hard:** no
- **Detail:** [Guidelines § 1, § 6, § 9](../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow),
  [`gateways/gateway-mcp/AGENTS.md`](../../gateways/gateway-mcp/AGENTS.md)

### C8 — Spectral rule changes are tested and grandfathered
- **Question:** Does a new or changed rule have a fixture and a `node --test` file? Does it pass on
  the current spec? If not, are existing violations allowlisted with an issue rather than fixed in a
  way that breaks published SDKs?
- **Hard:** yes
- **Detail:** [spectral-tests/README.md](spectral-tests/README.md), guidelines § 3.4

## 6. Data and persistence

None. The module stores no data. The spec describes storage behavior only through
`x-eventually-consistent` (queries read secondary storage).

## 7. Cross-cutting qualities

- **Security:** each operation declares `security` (enforced by `require-security-declared`) and its
  required permissions. Enforcement happens in the engine (commands) and `service/` (reads), not
  here. `resource-permissions.json` must stay equal to `AuthorizationResourceType.buildResourcePermissionsMap()`
  (`ResourcePermissionsRegistryTest`).
- **Tenancy:** `x-scope` separates physical-tenant operations from `/cluster/v2` ones. The
  multi-tenancy `tenantId` is an ordinary request field.
- **SDK friendliness:** the spec is the input for typed SDKs. Unions use `x-polymorphic-schema`,
  conditional presence uses `x-present-when`, and `oas3-valid-schema-example` stays at `warn` because
  changing `ScopeKey` from `oneOf` to `anyOf` breaks Java generation.
- **Licence:** the spec files and `gateway.proto` are under the Camunda License 1.0; generating gRPC
  stubs is not a derivative work ([clarification](../../licenses/Clarification-on-gRPC-code-generation.txt)).
- **API language:** no "flow node" anywhere (Spectral `no-flow-node-*` rules).

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../../SYSTEM.md) § 6, plus:

- `x-added-in-version` is the minor in which an operation first ships and is never updated. The
  verifier compares it with released baselines on every PR and on a schedule.
- Spec fixes backported to `stable/*` reach the docs automatically: the `camunda-docs` sync pulls
  every supported stable branch weekly (guidelines § 9.1).
- `@camunda/camunda-api-zod-schemas` is released separately (`publish-zod-schemas.yml`) and follows the
  spec by hand.
- Breaking changes to REST need forward-compatibility test updates on older branches, coordinated
  with `@camunda/core-features` (§ 7.6). Breaking changes to `gateway.proto` bypass `buf` only with
  `BREAKING CHANGE:`. TODO(confirm): who may approve that, and how gRPC client users are told.
- TODO(confirm): which external SDKs and products generate from the spec, so a breaking-change
  review knows whom to tell.

## 9. Testing expectations

- **Spec:** `openapi-lint` CI job: Spectral structural pass, per-file pass, then
  `node --test zeebe/gateway-protocol/spectral-tests/*.test.js`. Run both passes locally (OPENAPI_VALIDATION.md).
- **Versions:** `openapi-x-added-in-version-check` CI job and the scheduled
  `verify-x-added-in-version-annotations.yml`.
- **gRPC:** `protobuf-checks` (`buf breaking` against the base commit).
- **Downstream:** the build of `gateways/gateway-model`, `clients/java` and `gateway-protocol-impl`
  fails on a spec they can't generate from. Spec-sync and registry tests run in `zeebe/gateway-rest`.
  `assert-json-body` and the generated request-validation tests in the E2E suite check responses and
  400s against the spec. The forward-compatibility nightly catches breaking response changes.
- Detail: [guidelines § 7–8](../../docs/rest-api-endpoint-guidelines.md#7-testing-strategy).

## 10. Planning conventions

- Issues: `2. feature_request.yml` for new endpoints; `api-proposal.md` (labels `component/c8-api`,
  `kind/proposal`) only for changes to the API guidelines; `3. task.yml`, `4. epic breakdown.yml`.
- Labels: PRs touching `v2/` get `component/c8-api` (`.github/labeler.yml`); the `create-issue`
  skill files the gRPC/REST API surface under `component/zeebe-engine`. TODO(confirm): which label
  intake should use for a `gateway.proto` change.
- Spec format: the OpenAPI diff itself is the design artefact, reviewed in the governance channel.
  TODO(confirm): plans directory and ID prefix for plan refs.
- `.github/actions/paths-filter/action.yml` still lists `vacuum-ruleset.yaml` and
  `vacuum-ignores.yaml`, which no longer exist (Spectral replaced Vacuum). Harmless, but stale.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Spec / contract | The files under `v2/` (REST) and `gateway.proto` (gRPC); the only source of truth for the public API reference |
| Structural vs file-level pass | Spectral on the resolved entry point vs on each `v2/*.yaml` file; both run in CI |
| SDK-breaking | Runtime payloads still work, but generated SDK types change and break compilation |
| Alpha sentinel | The exact text "This endpoint is an alpha feature and may be subject to change in future releases." that the docs pipeline matches |
| Semantic kind | A named thing an operation establishes or requires (`x-semantic-*`), registered in `semantic-kinds.json` |
| Physical-tenant vs cluster-wide | `x-scope` values: one physical tenant's API vs `/cluster/v2` management |
| Advanced filter | A filter property that accepts a plain value or an operator object (`$eq`, `$in`, …) |
| Element | The API term for what older APIs called a "flow node" |
