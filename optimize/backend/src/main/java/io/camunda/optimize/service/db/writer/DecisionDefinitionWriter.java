/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.*;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import java.util.List;
import java.util.Set;

public interface DecisionDefinitionWriter {

  static final Set<String> FIELDS_TO_UPDATE =
      Set.of(
          DECISION_DEFINITION_KEY,
          DECISION_DEFINITION_VERSION,
          DECISION_DEFINITION_VERSION_TAG,
          DECISION_DEFINITION_NAME,
          DATA_SOURCE,
          TENANT_ID);

  void importDecisionDefinitions(
      List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos);

  void markDefinitionAsDeleted(final String definitionId);

  boolean markRedeployedDefinitionsAsDeleted(
      final List<DecisionDefinitionOptimizeDto> importedDefinitions);
}
