/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionReaderImpl implements DecisionDefinitionReader {

  private final DefinitionReader definitionReader;

  @Override
  public Optional<DecisionDefinitionOptimizeDto> getDecisionDefinition(
      final String decisionDefinitionKey,
      final List<String> decisionDefinitionVersions,
      final List<String> tenantIds) {
    return definitionReader.getFirstFullyImportedDefinitionFromTenantsIfAvailable(
        DefinitionType.DECISION, decisionDefinitionKey, decisionDefinitionVersions, tenantIds);
  }

  @Override
  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitions() {
    return definitionReader.getDefinitions(DefinitionType.DECISION, false, false, true);
  }

  @Override
  public String getLatestVersionToKey(String key) {
    return definitionReader.getLatestVersionToKey(DefinitionType.DECISION, key);
  }
}
