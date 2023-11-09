/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.*;


public interface DecisionDefinitionWriter {

  static final Set<String> FIELDS_TO_UPDATE = Set.of(
    DECISION_DEFINITION_KEY,
    DECISION_DEFINITION_VERSION,
    DECISION_DEFINITION_VERSION_TAG,
    DECISION_DEFINITION_NAME,
    DATA_SOURCE,
    TENANT_ID
  );

  void importDecisionDefinitions(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos);

  void markDefinitionAsDeleted(final String definitionId);

  boolean markRedeployedDefinitionsAsDeleted(final List<DecisionDefinitionOptimizeDto> importedDefinitions);

}
