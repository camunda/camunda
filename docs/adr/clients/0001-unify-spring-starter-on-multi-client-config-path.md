# Unify the Spring Boot starter on a single (multi-client) configuration path

**DRI**: Nicola Puppa

**Status**: Proposed

**Deciders**
- Nicola Puppa
- Jonathan Lukas (raised the proposal in the #57300 review)
- Meggle (megglos)

**Purpose**: Decide how the `camunda-spring-boot-starter` reconciles its two
configuration paths — legacy single-client (`camunda.client.*`) and the new
multi-client (`camunda.clients.<name>.*`) — into one, and pin down the
backward-compatibility guarantees that make that safe.

**Audience**: Engineers working on the `camunda-spring-boot-starter`
auto-configuration, and downstream teams (Connector runtime) that embed it.

## Context

The starter today has **two mutually-exclusive auto-configuration paths**,
selected at runtime by a pair of Spring `@Conditional` conditions:

- `OnSingleClientConfigurationCondition` → `CamundaAutoConfiguration`, driven by
  `camunda.client.*`. This is the mature path: it wires the `CamundaClient`
  bean, `@JobWorker` registration, startup deployment, `@ClusterVariables`, the
  actuator endpoint, metrics, the lifecycle event producer
  (`CamundaLifecycleEventProducer`), and test support
  (`CamundaSpringProcessTestContext`).
- `OnMultiClientConfigurationCondition` → `MultiCamundaClientAutoConfiguration`,
  driven by `camunda.clients.<name>.*`. This path is new and **partial**: it
  builds the per-client config overlay (`MultiCamundaClientPropertiesResolver`),
  the `CamundaClientRegistry`, one `CamundaClient` bean per configured client,
  and a `JsonMapper`. It does **not** wire job workers, deployment, the
  actuator, metrics, lifecycle events, or test support.

This split has two problems:

1. **Feature gap.** A multi-client application cannot run `@JobWorker`s, deploy
   resources on startup, expose the actuator, or emit lifecycle events. The
   `@JobWorker` fan-out mechanism (composite `(client, type)` keying and
   `MultiCamundaLifecycleEventProducer`, added under #56726) exists but is not
   activated because nothing publishes the per-client lifecycle events on the
   multi-client path.
2. **Mutual-exclusion fragility.** The two conditions must stay perfectly
   disjoint. Overlaps and ordering assumptions between them have already been a
   recurring source of CI flakiness, and every new starter feature must be
   taught about both paths.

The multi-client config model already resolves a single-client-style setup as
one entry: with the properties post-processor introduced under #56726, an unset
client name resolves to the logical name `default`, mirroring the REST API where
a `default` physical tenant always exists.

The properties layer is already normalized by an `EnvironmentPostProcessor`
(`CamundaClientPropertiesPostProcessor`) that remaps legacy `zeebe.client.*` →
`camunda.client.*` and derives implicit auth/mode before binding — so there is a
natural, already-present seam to also normalize `camunda.client.*` into the
multi-client shape.

## Decision

**D1. Collapse to a single auto-configuration path: multi-client, always.** The
unified path keeps the historic name `CamundaAutoConfiguration` (the former
multi-client `MultiCamundaClientAutoConfiguration` is repurposed into it, so
dependent modules that referenced `CamundaAutoConfiguration` need no rename). A
"single-client" application is just a multi-client application with exactly one
client named `default`. The former single-client auto-configuration's internals
(`CamundaClientProdAutoConfiguration`) and the two selection conditions
(`OnSingleClientConfigurationCondition`, `OnMultiClientConfigurationCondition`)
are removed.

**D2. Remap `camunda.client.*` → `camunda.clients.default.*` in the
`EnvironmentPostProcessor`.** `CamundaClientPropertiesPostProcessor` gains a step
that projects the single-client keys onto the `default` client entry. User-facing
config keys (`camunda.client.*`, and transitively the legacy `zeebe.client.*`)
are unchanged; the remap is transparent and happens before binding. Explicit
`camunda.clients.<name>.*` entries are left untouched, and an application may mix
the base (`camunda.client.*`, applied as the shared overlay base) with named
entries exactly as it does today.

**D3. Wire full feature parity on the unified path.** Job-worker registration,
startup deployment, `@ClusterVariables`, the actuator endpoint, metrics, the
lifecycle event producer, and test support are all provided on the multi-client
path. Lifecycle is driven by `MultiCamundaLifecycleEventProducer`, which emits
one created/closing event per configured client, activating the #56726
`@JobWorker` fan-out. For a single (`default`) client this reproduces exactly
one created/closing event, i.e. today's single-client lifecycle.

**D4. Preserve the public `camundaClient` bean name via an alias.** The default
client bean is registered as `defaultCamundaClient` and marked `@Primary`, so
`@Autowired CamundaClient` keeps resolving. A bean **alias** `camundaClient` is
registered for the primary client so that `@Qualifier("camundaClient")` and
`getBean("camundaClient")` — part of the current public surface — keep working.

**D5. Keep runtime behavior, lifecycle timing, and test support equivalent for
existing single-client apps.** The unification is a structural change; the
observable behavior of an app that only sets `camunda.client.*` must not change
(bean graph, worker start/stop timing, deployment-on-start, actuator payload,
metrics, `CamundaSpringProcessTestContext`).

## Consequences

- One code path to maintain; new starter features are implemented once. The
  mutual-exclusion conditions and their flakiness disappear.
- The multi-client feature set (#56725 per-client credentials, #56726
  `@JobWorker` fan-out) becomes usable end-to-end, unblocking the tests and
  documentation in #56727.
- The change is invisible to single-client users at the config level, but the
  **internal bean graph changes** (a `default` client bean + alias instead of a
  directly-named single client). Anything depending on internal bean names other
  than `camundaClient` could be affected; D4 covers the documented name.
- Requires backward-compatibility test coverage asserting single-client apps are
  unchanged (bean lookup by `camundaClient`, worker lifecycle, deployment,
  actuator, metrics, test support).

## Alternatives considered

- **Keep both paths, add the missing features to the multi-client path only.**
  Rejected: leaves the mutual-exclusion conditions (the flakiness source) in
  place and doubles the maintenance surface indefinitely.
- **Make single-client the sole path and layer multiplicity on top.** Rejected:
  the single-client path assumes exactly one target/identity throughout
  (properties, bean naming, lifecycle); generalizing it is a larger change than
  generalizing the already-multi-aware path, and it does not align with the REST
  API's "`default` tenant always exists" model.

## Backward-compatibility guarantees

- `camunda.client.*` and legacy `zeebe.client.*` keys keep working unchanged.
- `@Autowired CamundaClient` and `@Qualifier("camundaClient")` /
  `getBean("camundaClient")` keep working (D4).
- Workers, startup deployment, actuator, metrics, lifecycle events, and
  `CamundaSpringProcessTestContext` behave as before for single-client apps (D5).

## References

- Issue: camunda/camunda#57344
- Config model PR: camunda/camunda#57300; registry PR: camunda/camunda#57338
- Per-client credentials: camunda/camunda#56725; `@JobWorker` fan-out:
  camunda/camunda#56726
- Epic: camunda/camunda#56728

