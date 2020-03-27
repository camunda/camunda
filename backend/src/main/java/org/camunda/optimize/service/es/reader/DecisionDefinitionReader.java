/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionReader {
  private final DefinitionReader definitionReader;

  public Optional<DecisionDefinitionOptimizeDto> getDecisionDefinition(final String decisionDefinitionKey,
                                                                       final List<String> decisionDefinitionVersions,
                                                                       final List<String> tenantIds) {
    return tenantIds.stream()
      .map(tenantId -> getFullyImportedDecisionDefinition(
        decisionDefinitionKey,
        decisionDefinitionVersions,
        tenantId
      ))
      .filter(Optional::isPresent)
      .findFirst()
      .orElse(getFullyImportedDecisionDefinition(
        decisionDefinitionKey,
        decisionDefinitionVersions,
        null
      ));
  }

  public Optional<DecisionDefinitionOptimizeDto> getFullyImportedDecisionDefinition(
    final String decisionDefinitionKey,
    final List<String> decisionDefinitionVersions,
    final String tenantId) {
    return definitionReader.getDefinitionFromFirstTenantIfAvailable(
      DefinitionType.DECISION,
      decisionDefinitionKey,
      decisionDefinitionVersions,
      Lists.newArrayList(tenantId)
    );
  }

  public Optional<DecisionDefinitionOptimizeDto> getDecisionDefinitionByKeyAndEngineOmitXml(final String decisionDefinitionKey,
                                                                                            final String engineAlias) {
    return definitionReader.getDefinitionByKeyAndEngineOmitXml(
      DefinitionType.DECISION,
      decisionDefinitionKey,
      engineAlias
    );
  }

  public List<DecisionDefinitionOptimizeDto> getDecisionDefinitions(final boolean fullyImported,
                                                                    final boolean withXml) {
    return (List<DecisionDefinitionOptimizeDto>) definitionReader.getDefinitions(
      DefinitionType.DECISION,
      fullyImported,
      withXml
    );
  }

  public String getLatestVersionToKey(String key) {
    return definitionReader.getLatestVersionToKey(DefinitionType.DECISION, key);
  }
}
