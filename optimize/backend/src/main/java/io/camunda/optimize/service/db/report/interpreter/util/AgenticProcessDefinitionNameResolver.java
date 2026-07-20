/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.util;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;

/**
 * Shared, database-agnostic helper for the agentic process-definition-key group-by interpreters
 * (Elasticsearch and OpenSearch). It replaces each group's label with the human-readable name of
 * the definition's latest version, so the agentic "top token consumers" tile shows process names
 * instead of the raw BPMN process id (which, under Optimize's C7 naming, is what {@code
 * processDefinitionKey} holds).
 *
 * <p>The group key is left untouched. When no name can be resolved for a key, the label is left
 * as-is and {@link GroupByResult#getLabel()} keeps falling back to the key.
 */
public final class AgenticProcessDefinitionNameResolver {

  private AgenticProcessDefinitionNameResolver() {}

  public static void applyLatestVersionNameLabels(
      final CompositeCommandResult result, final DefinitionService definitionService) {
    result.getGroups().forEach(group -> resolveLatestVersionName(group, definitionService));
  }

  private static void resolveLatestVersionName(
      final GroupByResult group, final DefinitionService definitionService) {
    final String processDefinitionKey = group.getKey();
    if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
      return;
    }
    definitionService
        .getLatestCachedDefinitionOnAnyTenant(DefinitionType.PROCESS, processDefinitionKey)
        .map(DefinitionOptimizeResponseDto::getName)
        .filter(name -> !name.isBlank())
        .ifPresent(group::setLabel);
  }
}
