/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.List;

/**
 * Filters variable records by name, applying separate rules depending on whether the variable is
 * local (scoped to a sub-element) or root (scoped to the process instance itself).
 *
 * <p>A variable is considered local when its {@code scopeKey} differs from its {@code
 * processInstanceKey}; equal keys indicate a root variable.
 *
 * <p>Each scope is governed by its own {@link NameFilter}. If no rules are configured for a given
 * scope, all variables in that scope pass through. Non-variable records are always accepted.
 */
public final class VariableNameScopeFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final SemanticVersion MIN_BROKER_VERSION =
      new SemanticVersion(8, 10, 0, null, null);

  private final NameFilter localFilter;
  private final NameFilter rootFilter;
  private final boolean hasLocalRules;
  private final boolean hasRootRules;

  public VariableNameScopeFilter(
      final List<NameFilterRule> localInclusionRules,
      final List<NameFilterRule> localExclusionRules,
      final List<NameFilterRule> rootInclusionRules,
      final List<NameFilterRule> rootExclusionRules) {
    localFilter = new NameFilter(localInclusionRules, localExclusionRules);
    rootFilter = new NameFilter(rootInclusionRules, rootExclusionRules);
    hasLocalRules =
        (localInclusionRules != null && !localInclusionRules.isEmpty())
            || (localExclusionRules != null && !localExclusionRules.isEmpty());
    hasRootRules =
        (rootInclusionRules != null && !rootInclusionRules.isEmpty())
            || (rootExclusionRules != null && !rootExclusionRules.isEmpty());
  }

  @Override
  public boolean accept(final Record<?> record) {
    if (!(record.getValue() instanceof final VariableRecordValue variableRecordValue)) {
      return true;
    }

    if (VariableScope.isLocal(variableRecordValue)) {
      return !hasLocalRules || localFilter.accept(variableRecordValue.getName());
    } else {
      return !hasRootRules || rootFilter.accept(variableRecordValue.getName());
    }
  }

  @Override
  public SemanticVersion minRecordBrokerVersion() {
    return MIN_BROKER_VERSION;
  }
}
