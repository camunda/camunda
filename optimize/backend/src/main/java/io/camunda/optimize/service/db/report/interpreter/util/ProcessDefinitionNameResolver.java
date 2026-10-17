/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.util;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared, database-agnostic helper for the process-definition-key group-by interpreters
 * (Elasticsearch and OpenSearch). It replaces each group's label with the name of the matching
 * definition in the report, so per-process tiles show process names instead of the raw BPMN process
 * id (which, under Optimize's C7 naming, is what {@code processDefinitionKey} holds).
 *
 * <p>Names are taken only from the report's own definitions. For system-generated reports these are
 * injected at evaluation time from the definitions the user is authorized for, scoped to their
 * tenants, so a label can never come from a tenant the user cannot see.
 *
 * <p>The group key is left untouched. When no name can be resolved for a key, the label is left
 * as-is and {@link GroupByResult#getLabel()} keeps falling back to the key.
 */
public final class ProcessDefinitionNameResolver {

  private ProcessDefinitionNameResolver() {}

  public static void applyDefinitionLabels(
      final CompositeCommandResult result, final List<ReportDataDefinitionDto> definitions) {
    final Map<String, String> labelsByKey = labelsByKey(definitions);
    result.getGroups().forEach(group -> applyLabel(group, labelsByKey));
  }

  private static Map<String, String> labelsByKey(final List<ReportDataDefinitionDto> definitions) {
    final Map<String, String> labelsByKey = new HashMap<>();
    if (definitions == null) {
      return labelsByKey;
    }
    for (final ReportDataDefinitionDto definition : definitions) {
      final String label = labelOf(definition);
      if (definition.getKey() != null && label != null) {
        labelsByKey.putIfAbsent(definition.getKey(), label);
      }
    }
    return labelsByKey;
  }

  private static String labelOf(final ReportDataDefinitionDto definition) {
    if (isNotBlank(definition.getDisplayName())) {
      return definition.getDisplayName();
    }
    return isNotBlank(definition.getName()) ? definition.getName() : null;
  }

  private static void applyLabel(final GroupByResult group, final Map<String, String> labelsByKey) {
    final String processDefinitionKey = group.getKey();
    if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
      return;
    }
    final String label = labelsByKey.get(processDefinitionKey);
    if (label != null) {
      group.setLabel(label);
    }
  }

  private static boolean isNotBlank(final String value) {
    return value != null && !value.isBlank();
  }
}
