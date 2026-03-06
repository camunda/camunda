```yaml
---
applyTo: "zeebe/bpmn-model/src/**"
---
```
# BPMN Model Module

BPMN 2.0 XML parser, fluent process builder, and Zeebe-specific validator. Parses BPMN XML into a DOM-backed object model via the `camunda model-xml` framework, provides a fluent Java API to construct BPMN process definitions programmatically, and validates models against Zeebe engine constraints. This is a foundational module consumed by the Zeebe engine, REST gateway, client SDKs, and test utilities.

## Architecture

```
Bpmn (singleton, entry point)
 ├── BpmnParser          (XML→DOM parsing via model-xml)
 ├── BpmnModelInstance   (DOM-backed model wrapper)
 ├── instance/           (BPMN element interfaces — public API)
 │   ├── zeebe/          (Zeebe extension element interfaces, ZEEBE_NS)
 │   ├── bpmndi/         (Diagram interchange interfaces)
 │   ├── dc/             (Drawing coordinates: Bounds, Point, Font)
 │   └── di/             (Diagram elements: Edge, Shape, Waypoint)
 ├── impl/instance/      (Impl classes: XML↔Java mapping via registerType)
 │   ├── zeebe/          (Zeebe extension impl classes)
 │   ├── bpmndi/         (BPMNDI impl classes)
 │   ├── dc/             (DC impl classes)
 │   └── di/             (DI impl classes)
 ├── builder/            (Fluent builder API with F-bounded polymorphism)
 ├── validation/         (Validation framework: visitors + collectors)
 │   └── zeebe/          (Zeebe-specific validators registered in ZeebeDesignTimeValidators)
 ├── traversal/          (ModelWalker: depth-first visitor pattern)
 └── util/               (ModelUtil, VersionUtil)
```

## Key Abstractions

- **`Bpmn`** (`Bpmn.java`): Singleton entry point (`Bpmn.INSTANCE` is a `BpmnImpl`). Provides `readModelFromStream`, `writeModelToFile`, `createExecutableProcess`, `convertToString`. Registers all BPMN and Zeebe types in `doRegisterTypes()`.
- **`BpmnModelInstance`** (`BpmnModelInstance.java`): Wraps a DOM document, provides `getDefinitions()`, `newInstance(Class)`, `clone()`. Extends `org.camunda.bpm.model.xml.ModelInstance`.
- **`BpmnModelElementInstance`** (`instance/BpmnModelElementInstance.java`): Base interface for all BPMN elements. All 120+ element interfaces in `instance/` extend this.
- **`BpmnModelConstants`** / **`ZeebeConstants`**: String constants for XML element names, attribute names, and namespace URIs. Use these constants — never hardcode XML names.
- **`ModelWalker`** (`traversal/ModelWalker.java`): Depth-first, top-down traversal of a `BpmnModelInstance`. Skips non-executable processes. Used by validation and engine transformation.
- **`ValidationVisitor`** / **`CompositeValidationVisitor`**: Walk the model and dispatch to `ModelElementValidator` instances keyed by element type. Results collected via `ValidationResultsCollectorImpl`.
- **`BpmnTypeHierarchy`**: Caches the type-to-supertypes hierarchy for fast visitor dispatch in `TypeHierarchyVisitor`.

## Type Registration Pattern

Every element implementation has a static `registerType(ModelBuilder)` method that defines the XML element name, namespace, parent type, attributes, and child elements. This method is called from `Bpmn.doRegisterTypes()`.

```java
// Example: ServiceTaskImpl.registerType()
modelBuilder.defineType(ServiceTask.class, BPMN_ELEMENT_SERVICE_TASK)
    .namespaceUri(BPMN20_NS)
    .extendsType(Task.class)
    .instanceProvider(ctx -> new ServiceTaskImpl(ctx));
```

**Invariant**: Every new `*Impl` class MUST have `registerType(ModelBuilder)` and MUST be called in `Bpmn.doRegisterTypes()`. Forgetting this causes the element to be silently ignored during parsing.

## Builder API (F-Bounded Polymorphism)

Builders use the Curiously Recurring Template Pattern for type-safe chaining:

```
AbstractBpmnModelElementBuilder<B, E>
  └── AbstractBaseElementBuilder<B, E>       (createInstance, BPMNDI helpers)
      └── AbstractFlowElementBuilder<B, E>   (name, documentation)
          └── AbstractFlowNodeBuilder<B, E>  (connectTo, serviceTask, endEvent, etc.)
              └── AbstractActivityBuilder    (multiInstance, boundaryEvent)
                  └── AbstractTaskBuilder    (I/O mapping, execution listeners)
                      └── AbstractJobWorkerTaskBuilder (job type, retries, headers)
                          └── AbstractServiceTaskBuilder (implementation)
                              └── ServiceTaskBuilder (concrete)
```

- Concrete builders (e.g., `ServiceTaskBuilder`) are thin classes delegating to abstract parents.
- `ZeebeJobWorkerPropertiesBuilder<T>` and `ZeebeVariablesMappingBuilder<T>` are interfaces mixed into task builders for Zeebe-specific properties.
- Call `done()` on any builder to get the final `BpmnModelInstance`.
- Use `Bpmn.createExecutableProcess("id")` to start building.

## Zeebe Extension Elements

Zeebe-specific elements live under the `http://camunda.org/schema/zeebe/1.0` namespace (`ZEEBE_NS`). Each has an interface in `instance/zeebe/` and an impl in `impl/instance/zeebe/`.

Key extensions: `ZeebeTaskDefinition` (job type/retries), `ZeebeIoMapping` (input/output), `ZeebeCalledElement` (call activity), `ZeebeCalledDecision` (DMN), `ZeebeFormDefinition` (user task forms), `ZeebeAssignmentDefinition` (assignee/candidates), `ZeebeExecutionListener`, `ZeebeTaskListener`, `ZeebeProperties`, `ZeebeScript`, `ZeebeSubscription` (message correlation), `ZeebeLoopCharacteristics` (multi-instance), `ZeebePriorityDefinition`, `ZeebeLinkedResource`.

## Validation

- `ZeebeDesignTimeValidators.VALIDATORS` is the central list (~50 validators) checked before deployment.
- Two validator patterns:
  1. **`ExtensionElementsValidator`**: Declarative — asserts exactly one extension element of a type exists.
  2. **`ZeebeElementValidator`**: Declarative — chain `hasNonEmptyAttribute()` / `hasNonEmptyEnumAttribute()` assertions.
  3. **Custom validators**: Implement `ModelElementValidator<T>` directly for complex logic (e.g., `TimerEventDefinitionValidator`, `FlowNodeValidator`).
- Add new validators to the `VALIDATORS` list in `ZeebeDesignTimeValidators`.

## Adding a New BPMN/Zeebe Element

1. Create interface in `instance/` (or `instance/zeebe/` for extensions) extending `BpmnModelElementInstance`.
2. Create `*Impl` class in `impl/instance/` (or `impl/instance/zeebe/`) with `registerType(ModelBuilder)`.
3. Add constant names to `BpmnModelConstants` or `ZeebeConstants`.
4. Call `YourImpl.registerType(bpmnModelBuilder)` in `Bpmn.doRegisterTypes()`.
5. Add builder methods to the appropriate `Abstract*Builder` class if needed.
6. Add validator(s) to `ZeebeDesignTimeValidators.VALIDATORS`.
7. Add tests in `src/test/java/` for the element and its validation.

## Adding a New Builder Method

Add the method to the most specific `Abstract*Builder` that applies to all element types needing it. Use `getCreateSingleExtensionElement(Class)` from `AbstractBaseElementBuilder` to get or create Zeebe extension elements. Prefix Zeebe expression values with `=` using `asZeebeExpression()` or `ZEEBE_EXPRESSION_FORMAT`.

## Common Pitfalls

- Forgetting to register a new type in `Bpmn.doRegisterTypes()` — parsing silently ignores the element.
- Breaking the builder CRTP chain — concrete builder's `selfType` must be the concrete class itself.
- Adding XML constants without using `BpmnModelConstants`/`ZeebeConstants` — causes inconsistencies.
- Modifying Zeebe extensions without updating the validator in `ZeebeDesignTimeValidators`.
- Using the wrong namespace (`BPMN20_NS` vs `ZEEBE_NS`) for element registration.

## Testing

- Validation tests extend `AbstractZeebeValidationTest` and use `ProcessValidationUtil` to assert expected errors.
- Builder tests construct models via `Bpmn.createExecutableProcess()` and verify XML structure.
- `AbstractModelElementInstanceTest` verifies type metadata (attributes, child elements) for each BPMN element.
- Run: `./mvnw -pl zeebe/bpmn-model -am test -DskipITs -DskipChecks -T1C`

## Key Reference Files

- `Bpmn.java` — singleton, type registration, read/write/create API
- `ZeebeDesignTimeValidators.java` — central validator registry
- `AbstractFlowNodeBuilder.java` — core flow node builder with element chaining
- `BpmnModelConstants.java` + `ZeebeConstants.java` — all XML name constants
- `ModelWalker.java` — depth-first traversal used by validation and engine transformation
- `ServiceTaskImpl.java` — exemplary `registerType()` pattern for BPMN elements
- `ZeebeTaskDefinition.java` — exemplary Zeebe extension element interface