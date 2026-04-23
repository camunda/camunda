# ADR-0005: Frontend integration approach for Hub and Orchestration Cluster Admin UI

## Status

Proposed

## Context

For the unified identity Admin UI, we need one integration approach that works for:

- Hub integration (management plane), and
- standalone OC integration (execution plane).

Two approaches were discussed:

- Option 1: publish/use a web component and embed it in both runtimes.
- Option 2: integrate as an npm package (React component/library approach).

Current evidence in Camunda codebases and packages:

- In this monorepo, Operate, Tasklist, Identity, and Optimize already consume shared UI through npm packages (for example `@camunda/camunda-composite-components` and `C3Navigation`).
- There is no in-repo usage of `@camunda/ccma-saas-frontend`.
- `@camunda/ccma-saas-frontend` is published from `camunda/camunda-cloud-management-apps` (`packages/c4`) and exposes an ESM React library (`main`/`types`, React peer dependencies), i.e. not a web-component-first distribution.

## Decision

Use **Option 2 (npm package integration)** as the default integration model for the unified identity Admin UI in both Hub and OC.

Keep web components as a fallback pattern only if we need framework-agnostic embedding in a host that cannot consume React packages directly.

## Options considered

### Option 1 - Web components

Pros:

- Framework-agnostic host integration (React, Angular, Vue, or plain HTML hosts).
- Clear runtime isolation boundary for embedding.
- Potentially easier independent rollout if delivered as a drop-in element.

Cons:

- Additional integration layer for React-first applications already used in Camunda.
- Cross-cutting concerns (routing, theming, auth/session context, telemetry, feature flags) become harder at host/component boundaries.
- More operational and DX complexity for local development and type-safe API evolution.
- Likely duplication: React internals plus a wrapper/custom-element contract.

### Option 2 - npm package integration (chosen)

Pros:

- Matches existing Camunda frontend integration style in this monorepo (shared package consumption).
- Strong type safety and compile-time contracts through TypeScript.
- Simpler composition with existing React providers (routing, auth, i18n, theming, tracking).
- Easier versioning and dependency management per application.

Cons:

- Assumes compatible frontend stack (React/Carbon) in host applications.
- Requires dependency/version coordination across consumers.
- Less suitable for non-React hosts without an adapter/wrapper.

## Consequences

- Unified identity Admin UI integration in Hub and OC should be delivered as a versioned npm package with documented host contracts.
- The package API should stay narrow and stable (inputs, callbacks/events, auth/context expectations).
- If a non-React embedding target appears, we add a thin web-component adapter on top of the npm package instead of switching the primary delivery model.
- This ADR aligns with current Camunda practice where shared frontend capabilities are consumed as npm packages, while keeping a pragmatic escape hatch for framework-agnostic embedding.
