/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

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
    return definitionReader.getFirstFullyImportedDefinitionFromTenantsIfAvailable(
      DefinitionType.DECISION,
      decisionDefinitionKey,
      decisionDefinitionVersions,
      tenantIds
    );
  }

  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitions() {
    return definitionReader.getDefinitions(
      DefinitionType.DECISION,
      false,
      false,
      true
    );
  }

  public String getLatestVersionToKey(String key) {
    return definitionReader.getLatestVersionToKey(DefinitionType.DECISION, key);
  }
}
