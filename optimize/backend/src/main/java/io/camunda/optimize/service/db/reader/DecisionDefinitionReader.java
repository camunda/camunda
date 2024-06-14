/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import java.util.List;
import java.util.Optional;

public interface DecisionDefinitionReader {

  Optional<DecisionDefinitionOptimizeDto> getDecisionDefinition(
      final String decisionDefinitionKey,
      final List<String> decisionDefinitionVersions,
      final List<String> tenantIds);

  List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitions();

  String getLatestVersionToKey(String key);
}
