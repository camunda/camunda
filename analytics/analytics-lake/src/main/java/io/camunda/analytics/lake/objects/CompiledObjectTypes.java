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
 *       every correlated message under both;
 *   <li>every declared {@link ObjectTypes.Builder#belongsTo} parent type name must itself be a
 *       declared type — a single {@link CompiledObjectType} cannot check this on its own, since it
 *       only knows its own parent's <em>name</em>, not whether that name is actually declared;
 *   <li>no cycle may exist across the declared {@code belongsTo} chains (e.g. {@code a belongsTo b}
 *       and {@code b belongsTo a}) — same reasoning: only visible once every type is combined.
 * </ul>
 */
public final class CompiledObjectTypes {

  private final Map<String, CompiledObjectType> byVariableName;
  private final CompiledObjectType correlationKeyType;
  private final Map<String, List<CompiledObjectType>> closingTypesByProcessId;
  private final Map<String, String> parentTypeNameByChildType;

  private CompiledObjectTypes(
      final Map<String, CompiledObjectType> byVariableName,
      final CompiledObjectType correlationKeyType,
      final Map<String, List<CompiledObjectType>> closingTypesByProcessId,
      final Map<String, String> parentTypeNameByChildType) {
    this.byVariableName = byVariableName;
    this.correlationKeyType = correlationKeyType;
    this.closingTypesByProcessId = closingTypesByProcessId;
    this.parentTypeNameByChildType = parentTypeNameByChildType;
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
    final Map<String, String> parentTypeNameByChildType = new HashMap<>();
    for (final CompiledObjectType type : types) {
      if (type.parentTypeName() != null) {
        parentTypeNameByChildType.put(type.name(), type.parentTypeName());
      }
    }
    validateBelongsToDeclarations(names, parentTypeNameByChildType);
    return new CompiledObjectTypes(
        Map.copyOf(byVariableName),
        correlationKeyType,
        Map.copyOf(frozenClosingTypes),
        Map.copyOf(parentTypeNameByChildType));
  }

  /**
   * Validates every declared {@code belongsTo(...)} edge: the named parent type must itself be
   * declared, and no cycle may exist across the whole set of edges. A simple visited-set walk up
   * each edge's own parent chain catches both a direct 2-cycle ({@code a belongsTo b}, {@code b
   * belongsTo a}) and a longer one, without needing a general graph library for what is, in
   * practice, a handful of edges.
   */
  private static void validateBelongsToDeclarations(
      final Set<String> declaredNames, final Map<String, String> parentTypeNameByChildType) {
    for (final Map.Entry<String, String> edge : parentTypeNameByChildType.entrySet()) {
      final String childType = edge.getKey();
      final String parentType = edge.getValue();
      if (!declaredNames.contains(parentType)) {
        throw new IllegalArgumentException(
            "object type '"
                + childType
                + "' declares belongsTo('"
                + parentType
                + "') but no object type '"
                + parentType
                + "' is declared");
      }
    }
    for (final String childType : parentTypeNameByChildType.keySet()) {
      final Set<String> visited = new HashSet<>();
      String current = childType;
      while (current != null) {
        if (!visited.add(current)) {
          throw new IllegalArgumentException(
              "object type '"
                  + childType
                  + "' has a cyclic belongsTo chain (revisits '"
                  + current
                  + "')");
        }
        current = parentTypeNameByChildType.get(current);
      }
    }
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

  /**
   * Every declared {@code belongsTo(...)} edge, keyed by child type name with the value being its
   * declared parent type name — empty (never {@code null}) when no type declares one, which is the
   * common case. See {@code LakeTranslator}'s "Object fabric capture" javadoc section for how this
   * lookup drives the co-rooted containment derivation the root&times;non-root relation rule alone
   * cannot produce.
   */
  public Map<String, String> belongsToParentTypes() {
    return parentTypeNameByChildType;
  }
}
