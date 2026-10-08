---
architecture_md: 1
component: camunda/camunda/zeebe/bpmn-model
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: "The Zeebe BPMN model API: a Java 8 library that reads, writes, builds and validates BPMN 2.0 XML with the zeebe: extension namespace, used by the engine at deployment and by clients, tests and tools to create or inspect process models."
team: { name: camunda/core-features, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/zeebe] }
owns:
  - "Parsing and writing BPMN 2.0 XML (with BPMN DI, DC and DI) against the XSDs bundled in src/main/resources, and the Java object model of every BPMN element (io.camunda.zeebe.model.bpmn.instance and impl.instance)"
  - "The Java representation of the zeebe: extension namespace http://camunda.org/schema/zeebe/1.0: element and attribute names (ZeebeConstants), typed interfaces in instance.zeebe and their implementations in impl.instance.zeebe"
  - "The fluent builder API for creating models in code (Bpmn.createExecutableProcess and the builder package, including zeebe: extension builder methods)"
  - "Design-time validation: the rules that need only the model itself, collected in ZeebeDesignTimeValidators (which BPMN element and event types are supported at all, required and single-occurrence zeebe: extension elements, element-specific structure such as agentType vs hosting element)"
  - "Model traversal and lookup utilities (ModelWalker, TypeHierarchyVisitor, Query) and timer value parsing (util.time: ISO 8601 durations, cycles, dates)"
  - "The module's public API compatibility baseline (revapi.json)"
does_not_own:
  - { concept: "Validation that needs expressions, deployed resources or broker configuration (ZeebeRuntimeValidators, ZeebeConfigurationValidators, deployment-binding validators)", owner: camunda/camunda/zeebe/engine }
  - { concept: "Transforming a model into the executable process and executing its elements (behaviour of every BPMN element and zeebe: attribute)", owner: camunda/camunda/zeebe/engine }
  - { concept: "The zeebe: namespace descriptor used by Web Modeler and Desktop Modeler (zeebe.json moddle descriptor)", owner: camunda/zeebe-bpmn-moddle }
  - { concept: "Modeler properties-panel UI for zeebe: attributes", owner: bpmn-io/bpmn-js-properties-panel }
  - { concept: "FEEL expression parsing and evaluation", owner: camunda/camunda/zeebe/engine }
  - { concept: "Deploy commands and the deployment REST/gRPC endpoints that accept BPMN resources", owner: camunda/camunda/clients }
depends_on:
  - id: camunda-xml-model
    component: "Camunda 7 XML model API (org.camunda.bpm.model:camunda-xml-model)"
    kind: external
    contract: "ModelBuilder, ModelInstance, ModelElementType and the ModelElementValidator / ValidationResults API the whole model and its validators are built on"
    versions: "version.camunda in parent/pom.xml (7.24.0); upgrades via the parent POM"
    workaround_policy: never
  - id: slf4j
    component: "SLF4J (org.slf4j:slf4j-api)"
    kind: external
    contract: logging API
    versions: "version in parent/pom.xml"
    workaround_policy: never
  - id: build-parent
    component: camunda/camunda/parent
    kind: platform
    contract: "zeebe-parent POM: Java release level, revapi-maven-plugin with backwards.compat.version, license and formatting checks"
    versions: same monorepo release
    workaround_policy: never
consumers:
  - { who: camunda/camunda/zeebe/engine, via: "Bpmn.readModelFromStream on deployment, ZeebeDesignTimeValidators composed with the engine's runtime validators in BpmnValidator, instance API in the process transformers", promise: same monorepo release }
  - { who: camunda/camunda/clients, via: "BpmnModelInstance accepted by DeployProcessCommandStep1 and DeployResourceCommandStep1; compile dependency of io.camunda:camunda-client-java", promise: "revapi-checked against the previous minor; Java 8" }
  - { who: "Users' applications and tests (Maven Central, io.camunda:zeebe-bpmn-model in the BOM, and transitively through the Java client)", via: "Bpmn facade, builder API and instance API", promise: "revapi-checked against the previous minor; new methods on builder and zeebe: interfaces may appear at any minor" }
  - { who: "Camunda Process Test (testing/camunda-process-test-java, camunda-process-test-coverage, -spring)", via: "Bpmn and instance API for coverage reports and mocked child processes", promise: same monorepo release }
  - { who: "camunda/camunda/service and camunda/camunda/zeebe/exporters (through zeebe/util ProcessModelReader and exporter-common ProcessCacheUtil)", via: "instance API to read element names, call activities, ad-hoc sub-process activities from deployed XML", promise: same monorepo release }
  - { who: "Optimize (optimize/backend, not a system member), operate/common", via: "Bpmn and instance API (BpmnModelUtil)", promise: same monorepo release }
  - { who: "zeebe/test-util, zeebe/qa, qa/, microbenchmarks/, debug-cli/, zeebe/protocol-impl, zeebe/protocol-test-util", via: "builder API to create test models; instance API", promise: same monorepo release }
exposes:
  - { contract: "Bpmn facade and BpmnModelInstance (read, write, validate against the XSDs, create)", spec: src/main/java/io/camunda/zeebe/model/bpmn/Bpmn.java, policy: "Maven artifact io.camunda:zeebe-bpmn-model, Apache 2.0, Java 8, in the BOM; revapi against backwards.compat.version (previous minor)" }
  - { contract: "Model instance API (BPMN 2.0, BPMN DI, and zeebe: extension interfaces)", spec: src/main/java/io/camunda/zeebe/model/bpmn/instance, policy: "revapi; adding methods to interfaces in instance.zeebe is allowed because consumers don't implement them (revapi.json); other breaks need a justified revapi.json entry" }
  - { contract: "Fluent builder API", spec: src/main/java/io/camunda/zeebe/model/bpmn/builder, policy: "revapi; adding methods to builder interfaces and new superclasses of abstract builders are allowed (revapi.json): callers use the concrete builders and don't extend the abstract ones" }
  - { contract: "zeebe: BPMN extension namespace http://camunda.org/schema/zeebe/1.0 (the XML users' .bpmn files carry)", spec: src/main/java/io/camunda/zeebe/model/bpmn/impl/ZeebeConstants.java, policy: "TODO(confirm): additive only; a model valid for a released version stays readable and valid; the namespace URI never changes" }
  - { contract: "Extension point: validation visitors", spec: src/main/java/io/camunda/zeebe/model/bpmn/validation/ValidationVisitor.java, policy: "a caller passes its own collection of camunda-xml-model ModelElementValidator to a ValidationVisitor and combines it with ZeebeDesignTimeValidators.VALIDATORS in a CompositeValidationVisitor walked by ModelWalker; extra validators can add errors and warnings, not remove built-in ones. The validator classes in validation.zeebe are internal and may be removed (revapi.json); only the VALIDATORS collection is meant to be used. TODO(confirm) whether this is supported outside the engine" }
  - { contract: "Extension point: Bpmn subclassing", spec: src/main/java/io/camunda/zeebe/model/bpmn/Bpmn.java, policy: "protected constructor and do* methods (doRegisterTypes, doReadModelFromInputStream, …) allow a subclass to register more element types; only BpmnImpl uses it in this repo. TODO(confirm) whether it is a supported extension point" }
constraints:
  - { id: C1, name: Public Java API compatibility (revapi), hard: true, ref: revapi.json }
  - { id: C2, name: Java 8 and a small Apache-licensed dependency set, hard: true, ref: pom.xml }
  - { id: C3, name: Models deployed to a released version stay readable and valid, hard: true, ref: src/main/java/io/camunda/zeebe/model/bpmn/impl/BpmnModelConstants.java }
  - { id: C4, name: Design-time or runtime validation, hard: true, ref: src/main/java/io/camunda/zeebe/model/bpmn/validation/zeebe/ZeebeDesignTimeValidators.java }
  - { id: C5, name: A new zeebe extension or element reaches every place, hard: true, ref: src/main/java/io/camunda/zeebe/model/bpmn/Bpmn.java }
  - { id: C6, name: Modeler descriptor stays aligned, hard: false, ref: "https://github.com/camunda/zeebe-bpmn-moddle/blob/main/resources/zeebe.json" }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/zeebe/bpmn-model

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The Zeebe BPMN model API turns BPMN 2.0 XML with `zeebe:` extensions into a Java object model and
back, builds models in code, and decides at design time whether a model is one Zeebe can deploy. The
engine uses it on every deployment; developers use it through the Java client and in tests. It is
part of the [Orchestration Cluster](../../SYSTEM.md) system, where [SYSTEM.md](../../SYSTEM.md) § 1
currently counts it inside `zeebe/engine` (see § 2).

## 2. Ownership boundary

**Owns:** the BPMN object model and XML parser/writer, the Java side of the `zeebe:` namespace,
the builder API, the design-time validation rules, traversal utilities, timer parsing, and the
module's revapi baseline. The full list is in the front matter.

Which BPMN element types Zeebe accepts is decided here first:
[`FlowElementValidator`](src/main/java/io/camunda/zeebe/model/bpmn/validation/zeebe/FlowElementValidator.java)
and `EventDefinitionValidator` reject unsupported types before the engine sees the model. What the
element *does* is the engine's.

Owner: the GitHub [CODEOWNERS](../../CODEOWNERS) has no line for `zeebe/bpmn-model`. The
fine-grained attribution file [`.codeowners`](../../.codeowners) (codeowners-plus) assigns
`/zeebe/bpmn-model/` to `@camunda/core-features`, and `.github/workflows/ci.yml` runs
`:zeebe-bpmn-model` in the "Zeebe modules owned by @camunda/core-features" group. Contact channel:
TODO(confirm).

Component granularity: [SYSTEM.md](../../SYSTEM.md) § 1, the
[engine draft](../engine/ARCHITECTURE.md) § 2 and the `clients` and `service` drafts treat
`bpmn-model` as part of `camunda/camunda/zeebe/engine`. This file describes it as a component of its
own, because it has its own ownership line, published artifact, Java level and API contract.
TODO(confirm): that the team wants it separate, and that SYSTEM.md lists it (role `engine` or
`contracts`).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| Validation that evaluates expressions, resolves deployed forms/decisions/processes, or reads broker config | `camunda/camunda/zeebe/engine` (`processing/deployment/model/validation`) | issue, `component/zeebe` (same team) |
| The behaviour of a BPMN element or `zeebe:` attribute at runtime | `camunda/camunda/zeebe/engine` | issue, `component/zeebe` (same team) |
| The `zeebe:` namespace in Web Modeler and Desktop Modeler | `camunda/zeebe-bpmn-moddle` (`resources/zeebe.json`) | issue in that repo; TODO(confirm) owning team |
| A properties-panel field for a `zeebe:` attribute | `bpmn-io/bpmn-js-properties-panel` (`src/provider/zeebe`) | issue in that repo |
| How a deploy command sends a model | `camunda/camunda/clients` | issue |

## 3. Structure

| Path (`io.camunda.zeebe.model.bpmn` …) | Contents |
|---|---|
| `Bpmn`, `BpmnModelInstance`, `Query` | Public facade: read, write, validate, create; `doRegisterTypes` registers every element type |
| `instance`, `instance.di`, `instance.dc`, `instance.bpmndi` | BPMN 2.0 and diagram interfaces |
| `instance.zeebe` | One interface per `zeebe:` extension element (`ZeebeTaskDefinition`, `ZeebeCalledElement`, `ZeebeAgentDefinition`, …) |
| `impl`, `impl.instance.*` | Implementations, `BpmnParser`, `BpmnModelConstants` (namespaces), `ZeebeConstants` (names) |
| `builder`, `builder.zeebe` | Fluent builders; `Abstract*Builder` carry the methods, concrete builders are what callers use |
| `validation`, `validation.zeebe` | `ValidationVisitor`, `CompositeValidationVisitor`; design-time validators and `ZeebeDesignTimeValidators` |
| `traversal`, `util`, `util.time` | `ModelWalker` and visitors; model and version utilities; timer parsing |
| `src/main/resources` | OMG XSDs (`BPMN20.xsd`, `BPMNDI.xsd`, `DC.xsd`, `DI.xsd`, `Semantic.xsd`) and `bpmn-model.properties` |

Direction: this module depends on no other Zeebe or Camunda module, only on `camunda-xml-model` and
SLF4J. The engine and everything else depend on it, never the reverse. Runtime-dependent rules
belong in the engine (C4).

## 4. Binding decisions

ADR index: [`zeebe/docs/adr/`](../docs/adr/README.md); cross-cutting ones in
[`docs/adr/`](../../docs/adr/README.md). No ADR is about this module itself. These ADRs added to its
contract:

- [0003](../docs/adr/0003-810-business-id-call-activity-propagation.md): a single optional
  `businessId` attribute on `zeebe:calledElement`, chosen over a new extension element.
- [0011](../docs/adr/0011-810-agent-definition-from-bpmn-marker.md): `zeebe:agentDefinition` with a
  fixed `agentType` set. The schema can't tie `agentType` to the hosting element, so validation
  checks the pair.
- The `revapi.json` justifications record lasting choices: `zeebe:` interfaces and builders aren't
  meant to be implemented, so new methods are allowed. `validation.zeebe` classes are internal. A
  feature that is "not officially supported yet" may change its builder signatures (compensation,
  execution listeners, conditional events).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Public Java API compatibility (revapi)
- **Question:** Does revapi pass against `backwards.compat.version` (previous minor, in
  `parent/pom.xml`)? If not, is the break allowed by a rule in [`revapi.json`](revapi.json), or does
  it need a new entry with a justification? Which users' builder code or client code breaks?
- **Hard:** yes
- **Detail:** [`revapi.json`](revapi.json); the release workflow sets `backwards.compat.version`
  (`camunda-platform-release.yml`). TODO(confirm): that step deletes `bpmn-model/<ignored-changes
  file>`, a path without the `zeebe/` prefix, and this module has no `ignored-changes.json`.
  Should temporary breaks go there or into `revapi.json`?

### C2 — Java 8 and a small Apache-licensed dependency set
- **Question:** Does the change use an API newer than Java 8 or add a dependency? The artifact is
  Apache 2.0 and reaches every Java client user's classpath.
- **Hard:** yes
- **Detail:** [`pom.xml`](pom.xml) (`version.java` 8), [`LICENSE`](LICENSE),
  [CONTRIBUTING](../../CONTRIBUTING.md) (language-level exceptions). TODO(confirm): whether Apache 2.0
  is a hard rule for new dependencies.

### C3 — Models deployed to a released version stay readable and valid
- **Question:** Can every `.bpmn` file that a released version accepted still be parsed and pass
  design-time validation? Are new `zeebe:` attributes and elements optional? Does a new rule reject
  models that customers already run, or that the engine must re-read on replay or
  migration?
- **Hard:** yes (TODO(confirm))
- **Detail:** `ZEEBE_NS` in
  [`BpmnModelConstants`](src/main/java/io/camunda/zeebe/model/bpmn/impl/BpmnModelConstants.java).
  TODO(confirm): whether the engine re-parses stored process XML after an update, which would make a
  stricter validator a runtime break, not only a deployment one.

### C4 — Design-time or runtime validation
- **Question:** Does the new rule need only the model? Then it goes here, in a validator registered
  in `ZeebeDesignTimeValidators`. Does it need expression parsing, other deployed resources or
  broker config? Then it goes in the engine (`ZeebeRuntimeValidators`,
  `ZeebeConfigurationValidators`).
- **Hard:** yes
- **Detail:** [`ZeebeDesignTimeValidators`](src/main/java/io/camunda/zeebe/model/bpmn/validation/zeebe/ZeebeDesignTimeValidators.java),
  the engine's `BpmnValidator` composing the three sets.

### C5 — A new zeebe extension or element reaches every place
- **Question:** For a new `zeebe:` element or attribute: name in `ZeebeConstants`, interface in
  `instance.zeebe`, implementation registered in `Bpmn.doRegisterTypes`, builder method, validator,
  entry in `ExtensionElementDuplicationValidators` if it may occur once, round-trip and validation
  tests. Then outside this module: engine transformer and behaviour, `zeebe-bpmn-moddle`,
  properties panel, docs. For a newly supported BPMN element: `FlowElementValidator` here and the
  engine's support. Who does each step, and in which release?
- **Hard:** yes
- **Detail:** [`Bpmn.java`](src/main/java/io/camunda/zeebe/model/bpmn/Bpmn.java),
  [ADR 0011 sources](../docs/adr/0011-810-agent-definition-from-bpmn-marker.md#source) (model issue
  #58975 before engine issue #58976).

### C6 — Modeler descriptor stays aligned
- **Question:** Does `camunda/zeebe-bpmn-moddle` describe the same element/attribute name, type,
  allowed parents and multiplicity? Will Modeler users be able to set it before the engine accepts it?
- **Hard:** no (TODO(confirm))
- **Detail:** [zeebe.json](https://github.com/camunda/zeebe-bpmn-moddle/blob/main/resources/zeebe.json)
  (same namespace URI). Nothing checks the two definitions against each other.

## 6. Data and persistence

No store of its own. The XML it parses is stored by others: deployed resources in the engine's
state and in secondary storage, and `.bpmn` files in users' projects. That is why C3 matters.

## 7. Cross-cutting qualities

- **Security:** parses untrusted XML on every deployment through `camunda-xml-model`.
  TODO(confirm): where XXE and entity expansion are disabled, and who checks that on upgrades of
  `camunda-xml-model`.
- **Performance:** parsing and validation run on the deployment path in the engine. Benchmarks are
  in `microbenchmarks/` (`LargeProcessDeploymentBenchmark`).
- **Error messages:** validation messages reach users as deployment rejections; their wording is
  user-facing. TODO(confirm) whether tests or docs depend on exact texts.

## 8. Delivery

As Orchestration Cluster SYSTEM.md (one monorepo release). Specific here: published to Maven Central
as `io.camunda:zeebe-bpmn-model`, managed in the BOM, and included in the dependency snapshot and
Snyk scans with the client artifacts (`ci.yml`, `zeebe-snyk.yml`). Features "not officially
supported yet" may change their builder API (see `revapi.json`).
TODO(confirm): backport rules for a new validation rule on a `stable/*` branch, which can reject
models a patch earlier accepted.

## 9. Testing expectations

- Unit tests in this module: round-trip tests per `zeebe:` element (`instance/zeebe/*Test`),
  validation tests based on `AbstractZeebeValidationTest` and `ProcessValidationUtil`, builder tests.
- Run: `./mvnw verify -pl zeebe/bpmn-model -DskipTests=false -Dquickly`; revapi runs when checks
  aren't skipped.
- Most tests are still JUnit 4 (`org.junit.Test`); migrate tests you change to JUnit 5 (root
  AGENTS.md).
- Engine-level deployment and execution tests for the same feature live in `zeebe/engine`.

## 10. Planning conventions

- Issues: label `component/zeebe` (used on #58975), templates `2. feature_request.yml`,
  `3. task.yml`, `4. epic breakdown.yml`. TODO(confirm): which label the team triages.
- ADRs that change the `zeebe:` namespace go to [`zeebe/docs/adr/`](../docs/adr/README.md)
  (`NNNN-<minor>-…`).
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

- **Extension element / attribute:** a `zeebe:` element under `bpmn:extensionElements`, or a
  `zeebe:` attribute on a BPMN element. It carries the Zeebe-specific configuration (job type, I/O
  mappings, called element).
- **Design-time validation:** rules on the model alone, in this module. **Runtime validation:** rules
  that need expressions, deployed resources or config, in the engine. Both run at deployment.
- **zeebe-bpmn-model vs zeebe-bpmn-moddle:** this Java library vs the JavaScript descriptor the
  Modelers use for the same namespace.
- **Not officially supported (yet):** a BPMN feature present in the model before the engine supports
  it. Its API may still change.
