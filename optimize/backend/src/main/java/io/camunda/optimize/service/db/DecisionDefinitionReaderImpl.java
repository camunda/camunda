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
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DecisionDefinitionReaderImpl implements DecisionDefinitionReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DecisionDefinitionReaderImpl.class);
  private final DefinitionReader definitionReader;

  public DecisionDefinitionReaderImpl(final DefinitionReader definitionReader) {
    this.definitionReader = definitionReader;
  }

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
  public String getLatestVersionToKey(final String key) {
    return definitionReader.getLatestVersionToKey(DefinitionType.DECISION, key);
  }
}
