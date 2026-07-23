/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for declaring an object-centric process mining (OCPM) object type — configuration-as-
 * code, mirroring {@code io.camunda.analytics.lake.metrics.EntityMetrics}'s own declaration style:
 *
 * <pre>{@code
 * ObjectTypes.declare("order")
 *     .identifiedBy(ObjectTypes.variable("orderId"))
 *     .identifiedBy(ObjectTypes.correlationKey("orderId"))
 *     .closes(ObjectTypes.onProcessCompletion("orderFulfillment"))
 *     .belongsTo("customer")
 *     .build();
 * }</pre>
 *
 * <p>{@link Builder#build()} validates the declaration (non-blank name, at least one identifier,
 * every identifier source itself non-blank, every closing rule itself non-blank, a non-blank {@code
 * belongsTo(...)} parent type name that is not the declaring type's own name) and compiles it into
 * a {@link CompiledObjectType}. Combine one or more compiled types via {@link
 * CompiledObjectTypes#of} before wiring them into {@code
 * io.camunda.analytics.lake.translate.LakeTranslator} — that step performs the cross-type
 * validation (at most one correlation-key declarer; no two types claiming the same variable name;
 * every {@code belongsTo(...)} parent type name must itself be declared; no cycle across the
 * declared {@code belongsTo} chains) and builds the fast lookups the translator needs, including
 * the {@code closes(...)} declarations' own process-id &rarr; closing-types lookup (see {@link
 * CompiledObjectTypes#closingTypesForProcess}) and the {@code belongsTo(...)} declarations' own
 * child-type &rarr; parent-type lookup (see {@link CompiledObjectTypes#belongsToParentTypes}).
 *
 * <p>{@code closes(...)} is optional: an object type that declares none simply never closes (see
 * {@code LakeTranslator}'s "Object lifecycle capture" javadoc section for the default-open case
 * this is the whole of).
 *
 * <p>{@code belongsTo(...)} is optional and at most a single parent type name: it exists because
 * co-rooted sightings (e.g. an order and its customer, both sighted at the same process instance's
 * root scope) can never be paired by {@code LakeTranslator}'s root&times;non-root relation rule —
 * that rule can only order a root-scope sighting against a non-root-scope one, and scope nesting
 * gives it no way to order two sightings that are both at the root. The declaration supplies the
 * hierarchy the scope tree cannot (see {@code LakeTranslator}'s "Object fabric capture" javadoc
 * section for the derivation itself).
 */
public final class ObjectTypes {

  private ObjectTypes() {}

  public static Builder declare(final String name) {
    return new Builder(name);
  }

  /** See {@link IdentifierSource.VariableIdentifier}'s own javadoc. */
  public static IdentifierSource variable(final String variableName) {
    return new IdentifierSource.VariableIdentifier(variableName);
  }

  /** See {@link IdentifierSource.CorrelationKeyIdentifier}'s own javadoc. */
  public static IdentifierSource correlationKey(final String label) {
    return new IdentifierSource.CorrelationKeyIdentifier(label);
  }

  /** See {@link ClosingRule.OnProcessCompletion}'s own javadoc. */
  public static ClosingRule onProcessCompletion(final String bpmnProcessId) {
    return new ClosingRule.OnProcessCompletion(bpmnProcessId);
  }

  /** Mutable, single-use builder returned by {@link #declare}. */
  public static final class Builder {

    private final String name;
    private final List<IdentifierSource> identifiers = new ArrayList<>();
    private final List<ClosingRule> closingRules = new ArrayList<>();
    private String parentTypeName;

    private Builder(final String name) {
      this.name = name;
    }

    /** Declares one identifier source; may be called more than once (any identifier qualifies). */
    public Builder identifiedBy(final IdentifierSource source) {
      if (source == null) {
        throw new IllegalArgumentException(
            "object type '" + name + "': identifier source must not be null");
      }
      identifiers.add(source);
      return this;
    }

    /**
     * Declares one closing rule; may be called more than once (any one of them closes an open
     * instance of this type — see {@link ClosingRule}'s own javadoc). Omit entirely for a type that
     * never closes (the default-open case).
     */
    public Builder closes(final ClosingRule rule) {
      if (rule == null) {
        throw new IllegalArgumentException(
            "object type '" + name + "': closing rule must not be null");
      }
      closingRules.add(rule);
      return this;
    }

    /**
     * Declares this type's parent type for containment purposes (see this class's own javadoc for
     * why this is needed alongside {@code LakeTranslator}'s root&times;non-root relation rule). At
     * most one parent type name per declaration; {@code parentTypeName}'s existence among the types
     * eventually combined via {@link CompiledObjectTypes#of} — and the absence of any cycle across
     * every declared {@code belongsTo} chain — is validated there, not here (a single declaration
     * cannot see its sibling declarations).
     */
    public Builder belongsTo(final String parentTypeName) {
      if (parentTypeName == null) {
        throw new IllegalArgumentException(
            "object type '" + name + "': parent type name must not be null");
      }
      this.parentTypeName = parentTypeName;
      return this;
    }

    /**
     * Validates the declaration and compiles it.
     *
     * @throws IllegalArgumentException with a precise message identifying which rule failed
     */
    public CompiledObjectType build() {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("object type name must not be blank");
      }
      if (identifiers.isEmpty()) {
        throw new IllegalArgumentException(
            "object type '"
                + name
                + "' declares no identifier — at least one identifiedBy(...) is required");
      }
      for (final IdentifierSource source : identifiers) {
        final String sourceName =
            switch (source) {
              case IdentifierSource.VariableIdentifier v -> v.variableName();
              case IdentifierSource.CorrelationKeyIdentifier c -> c.label();
            };
        if (sourceName == null || sourceName.isBlank()) {
          throw new IllegalArgumentException(
              "object type '" + name + "': an identifier source name must not be blank");
        }
      }
      for (final ClosingRule rule : closingRules) {
        final String processId =
            switch (rule) {
              case ClosingRule.OnProcessCompletion c -> c.bpmnProcessId();
            };
        if (processId == null || processId.isBlank()) {
          throw new IllegalArgumentException(
              "object type '" + name + "': a closing rule's process id must not be blank");
        }
      }
      if (parentTypeName != null) {
        if (parentTypeName.isBlank()) {
          throw new IllegalArgumentException(
              "object type '" + name + "': belongsTo parent type name must not be blank");
        }
        if (parentTypeName.equals(name)) {
          throw new IllegalArgumentException(
              "object type '"
                  + name
                  + "' cannot declare belongsTo('"
                  + name
                  + "') -- self-reference");
        }
      }
      return new CompiledObjectType(
          name, List.copyOf(identifiers), List.copyOf(closingRules), parentTypeName);
    }
  }
}
