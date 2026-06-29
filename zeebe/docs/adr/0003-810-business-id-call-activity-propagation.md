# Business ID call activity propagation: single `businessId` attribute on `zeebe:calledElement`

**DRI**: Ev Bilske

**Status**: Accepted (8.10)

**Purpose**: Defines the BPMN extension and semantics by which a call activity sets or propagates a
child process instance's Business ID.

**Audience**: Zeebe engine engineers, BPMN modeling-tool engineers, and AI agents working on call
activities or Business ID.

## Context

A Business ID is a domain-specific identifier assigned to a process instance (e.g. an order number or
case reference). As of 8.9, a child process instance created by a call activity can only inherit its
parent's Business ID — there is no way to assign a different Business ID, derive one, or start a child
without a Business ID.

This is too restrictive: a parent process may need to give its child a distinct domain identifier
(e.g. a transaction or loan reference), derive a per-instance identifier for multi-instance call
activities, or start an orchestration-helper child that is unrelated to the parent's business entity.
The outcome: the call activity gains a single optional `businessId` attribute on `zeebe:calledElement`
whose presence and value control whether the parent's Business ID is propagated, and what the child's
Business ID becomes.

## Decision

**D1. Add a single optional `businessId` attribute to `zeebe:calledElement`.** Child Business ID
configuration lives as one attribute alongside the existing propagation attributes
(`propagateAllChildVariables`, `propagateAllParentVariables`) and mirrors how the `processId`
attribute already works on the same element.

```xml
<bpmn:callActivity id="Activity_18p8nh3">
  <bpmn:extensionElements>
    <zeebe:calledElement businessId="=differentId" propagateAllChildVariables="false" />
  </bpmn:extensionElements>
</bpmn:callActivity>
```

**D2. Attribute presence controls propagation.** When the `businessId` attribute is **missing**, the
parent's Business ID is propagated to the child — preserving 8.9 cluster behaviour. When it is
**present**, the parent's Business ID is **not** propagated; the child's Business ID is determined
solely from the attribute value (D3).

**D3. The attribute value is interpreted by its form.** A value starting with `=` is a FEEL
expression, evaluated by the engine at child instance creation. An empty string sets the child's
Business ID to null (matching 8.9 engine behaviour for an absent Business ID). Any other value is a
literal Business ID.

**D4. Invalid configuration is rejected at deploy or raised as an incident at runtime.** A child
`businessId` defined with an invalid FEEL expression rejects the process on deployment. At runtime, a
FEEL expression that evaluates to null, to an empty string, or to an invalid value (e.g. longer than
256 characters), and a literal that violates Business ID constraints (e.g. longer than 256
characters), raise an incident at the call activity.

## Alternatives considered

- **Separate `businessId` + `businessIdExpression` attributes.** Mirrors the
  `processId` / `processIdExpression` pair, but it is ambiguous which attribute is authoritative when
  both are set, deviates from the `=`-prefix FEEL convention used elsewhere, and the
  `processIdExpression` precedent is not parsed by the current engine nor written by current tooling.
- **A new `zeebe:childBusinessId` element sibling to `zeebe:calledElement`.** Offers more future
  extensibility, but the child Business ID is tightly coupled to `calledElement` and is not meaningful
  on its own, so a separate element does not justify the added complexity.
- **A `propagateBusinessId` boolean plus a `childBusinessId` attribute.** Makes the author's
  propagation intent explicit in the diagram, but is more complex and admits ambiguous permutations
  (e.g. `propagateBusinessId="true"` with `childBusinessId` set).

## Consequences

- The default (attribute absent) keeps 8.9 propagation behaviour, so existing models are unaffected.
- A single attribute carries four distinct behaviours (propagate / literal / FEEL / null) selected by
  value form; modeling tools must surface this so the absent-vs-empty-vs-`=` distinction is clear to
  authors.
- FEEL evaluation happens at child instance creation, so propagation failures (null/empty/oversized
  results) surface as runtime incidents at the call activity rather than at deploy time.
- Future extensions — deriving the child Business ID from the parent's (e.g. `=businessId + "-suffix"`)
  or a literal fallback when a FEEL expression yields null — are accommodated by the FEEL form but are
  not part of this decision.

## Source

- [Solution Proposal: Business ID Call Activity Propagation BPMN Extension](https://docs.google.com/document/d/1VZV6BZRxIdDs4bVCedG_jY6LlGGU2hCRw9rE0h2ioIM/edit?tab=t.kcyjumf9ngd4) (internal)
- [PDP epic: Business ID Visibility and Filtering](https://github.com/camunda/product-hub/issues/3436) (internal)
- [Business ID documentation](https://docs.camunda.io/docs/next/components/concepts/business-id/)

