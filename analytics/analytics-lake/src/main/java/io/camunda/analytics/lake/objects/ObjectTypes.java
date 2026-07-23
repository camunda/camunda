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
 *     .build();
 * }</pre>
 *
 * <p>{@link Builder#build()} validates the declaration (non-blank name, at least one identifier,
 * every identifier source itself non-blank) and compiles it into a {@link CompiledObjectType}.
 * Combine one or more compiled types via {@link CompiledObjectTypes#of} before wiring them into
 * {@code io.camunda.analytics.lake.translate.LakeTranslator} — that step performs the cross-type
 * validation (at most one correlation-key declarer; no two types claiming the same variable name)
 * and builds the fast lookups the translator needs.
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

  /** Mutable, single-use builder returned by {@link #declare}. */
  public static final class Builder {

    private final String name;
    private final List<IdentifierSource> identifiers = new ArrayList<>();

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
      return new CompiledObjectType(name, List.copyOf(identifiers));
    }
  }
}
