/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Objects;
import java.util.Set;

/**
 * Filters variable records by inferred JSON type, applying separate rules depending on whether the
 * variable is local (scoped to a sub-element) or root (scoped to the process instance itself).
 *
 * <p>A variable is considered local when its {@code scopeKey} differs from its {@code
 * processInstanceKey}; equal keys indicate a root variable.
 *
 * <p>Each scope is governed by its own inclusion/exclusion type set. If no rules are configured for
 * a given scope, all variables in that scope pass through. Non-variable records are always
 * accepted.
 */
public final class VariableTypeScopeFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final SemanticVersion MIN_BROKER_VERSION =
      new SemanticVersion(8, 10, 0, null, null);

  private final ObjectMapper objectMapper;
  private final Set<VariableValueType> allowedLocalTypes;
  private final Set<VariableValueType> allowedRootTypes;
  private final boolean hasLocalRules;
  private final boolean hasRootRules;

  public VariableTypeScopeFilter(
      final Set<VariableValueType> localInclusion,
      final Set<VariableValueType> localExclusion,
      final Set<VariableValueType> rootInclusion,
      final Set<VariableValueType> rootExclusion) {
    this(new ObjectMapper(), localInclusion, localExclusion, rootInclusion, rootExclusion);
  }

  public VariableTypeScopeFilter(
      final ObjectMapper objectMapper,
      final Set<VariableValueType> localInclusion,
      final Set<VariableValueType> localExclusion,
      final Set<VariableValueType> rootInclusion,
      final Set<VariableValueType> rootExclusion) {

    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    Objects.requireNonNull(localInclusion, "localInclusion must not be null");
    Objects.requireNonNull(localExclusion, "localExclusion must not be null");
    Objects.requireNonNull(rootInclusion, "rootInclusion must not be null");
    Objects.requireNonNull(rootExclusion, "rootExclusion must not be null");

    allowedLocalTypes = VariableValueType.buildAllowedSet(localInclusion, localExclusion);
    allowedRootTypes = VariableValueType.buildAllowedSet(rootInclusion, rootExclusion);
    hasLocalRules = !localInclusion.isEmpty() || !localExclusion.isEmpty();
    hasRootRules = !rootInclusion.isEmpty() || !rootExclusion.isEmpty();
  }

  @Override
  public boolean accept(final Record<?> record) {
    if (!(record.getValue() instanceof final VariableRecordValue variableRecordValue)) {
      return true;
    }

    if (VariableScope.isLocal(variableRecordValue)) {
      if (!hasLocalRules) {
        return true;
      }
      return allowedLocalTypes.contains(
          VariableValueType.infer(objectMapper, variableRecordValue.getValue()));
    } else {
      if (!hasRootRules) {
        return true;
      }
      return allowedRootTypes.contains(
          VariableValueType.infer(objectMapper, variableRecordValue.getValue()));
    }
  }

  @Override
  public SemanticVersion minRecordBrokerVersion() {
    return MIN_BROKER_VERSION;
  }
}
