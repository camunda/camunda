/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A validated registry of every {@link CompiledObjectType} the lake fabric knows about, built once
 * (e.g. in {@code io.camunda.analytics.lake.LakePocApp}) and shared by {@code
 * io.camunda.analytics.lake.translate.LakeTranslator}'s own object-sighting fold. Compiling the
 * types together (rather than trusting each one in isolation) is what lets this class enforce the
 * cross-type rules a single {@link CompiledObjectType} cannot check on its own:
 *
 * <ul>
 *   <li>no two object types may declare the same name;
 *   <li>no two object types may claim the same {@link IdentifierSource.VariableIdentifier} variable
 *       name — the translator's {@code variableName -> type} lookup could not otherwise be a
 *       single-valued map;
 *   <li>at most one object type may declare {@link IdentifierSource.CorrelationKeyIdentifier}
 *       identity — a v1 limitation forced by {@code ProcessMessageSubscriptionRecordValue}/{@code
 *       MessageStartEventSubscriptionRecordValue} carrying a correlation key's <em>value</em> but
 *       never which variable produced it, so a correlated record cannot itself disambiguate which
 *       declared type it belongs to; two types both claiming correlation-key identity would sight
 *       every correlated message under both.
 * </ul>
 */
public final class CompiledObjectTypes {

  private final Map<String, CompiledObjectType> byVariableName;
  private final CompiledObjectType correlationKeyType;
  private final Map<String, List<CompiledObjectType>> closingTypesByProcessId;

  private CompiledObjectTypes(
      final Map<String, CompiledObjectType> byVariableName,
      final CompiledObjectType correlationKeyType,
      final Map<String, List<CompiledObjectType>> closingTypesByProcessId) {
    this.byVariableName = byVariableName;
    this.correlationKeyType = correlationKeyType;
    this.closingTypesByProcessId = closingTypesByProcessId;
  }

  public static CompiledObjectTypes of(final CompiledObjectType... types) {
    return of(List.of(types));
  }

  /**
   * @throws IllegalArgumentException with a precise message identifying which cross-type rule
   *     failed — see this class's own javadoc for the full list
   */
  public static CompiledObjectTypes of(final List<CompiledObjectType> types) {
    if (types.isEmpty()) {
      throw new IllegalArgumentException("at least one object type must be declared");
    }
    final Set<String> names = new HashSet<>();
    final Map<String, CompiledObjectType> byVariableName = new HashMap<>();
    CompiledObjectType correlationKeyType = null;
    for (final CompiledObjectType type : types) {
      if (!names.add(type.name())) {
        throw new IllegalArgumentException(
            "object type '" + type.name() + "' is declared more than once");
      }
      for (final IdentifierSource source : type.identifiers()) {
        if (source instanceof final IdentifierSource.VariableIdentifier variable) {
          final CompiledObjectType clash =
              byVariableName.putIfAbsent(variable.variableName(), type);
          if (clash != null) {
            throw new IllegalArgumentException(
                "variable '"
                    + variable.variableName()
                    + "' identifies both object types '"
                    + clash.name()
                    + "' and '"
                    + type.name()
                    + "' — ambiguous, at most one object type may claim a given variable name");
          }
        } else if (source instanceof IdentifierSource.CorrelationKeyIdentifier) {
          if (correlationKeyType != null) {
            throw new IllegalArgumentException(
                "both '"
                    + correlationKeyType.name()
                    + "' and '"
                    + type.name()
                    + "' declare correlation-key identity — at most one object type may (v1"
                    + " limitation, see this class's own javadoc)");
          }
          correlationKeyType = type;
        }
      }
    }
    final Map<String, List<CompiledObjectType>> closingTypesByProcessId = new HashMap<>();
    for (final CompiledObjectType type : types) {
      for (final ClosingRule rule : type.closingRules()) {
        final String processId =
            switch (rule) {
              case ClosingRule.OnProcessCompletion c -> c.bpmnProcessId();
            };
        closingTypesByProcessId.computeIfAbsent(processId, ignored -> new ArrayList<>()).add(type);
      }
    }
    final Map<String, List<CompiledObjectType>> frozenClosingTypes = new HashMap<>();
    closingTypesByProcessId.forEach(
        (processId, closingTypes) -> frozenClosingTypes.put(processId, List.copyOf(closingTypes)));
    return new CompiledObjectTypes(
        Map.copyOf(byVariableName), correlationKeyType, Map.copyOf(frozenClosingTypes));
  }

  /**
   * The object type identified by root- or non-root-scope variable {@code variableName}, or {@code
   * null}.
   */
  public CompiledObjectType variableIdentifiedType(final String variableName) {
    return byVariableName.get(variableName);
  }

  /** The single object type declaring correlation-key identity, or {@code null} if none does. */
  public CompiledObjectType correlationKeyIdentifiedType() {
    return correlationKeyType;
  }

  /**
   * Every declared object type that {@link ObjectTypes.Builder#closes(ClosingRule)} names {@code
   * bpmnProcessId} as one of its closing processes — empty (never {@code null}) when no type
   * declares one, which is the common case for most processes in a deployment (see {@code
   * LakeTranslator}'s "Object lifecycle capture" javadoc section for how this lookup gates the
   * closing-emission hot path so a non-closing process's completion costs one cheap map lookup and
   * nothing more).
   */
  public List<CompiledObjectType> closingTypesForProcess(final String bpmnProcessId) {
    return closingTypesByProcessId.getOrDefault(bpmnProcessId, List.of());
  }
}
