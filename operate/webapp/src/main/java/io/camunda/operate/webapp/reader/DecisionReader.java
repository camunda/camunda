/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import java.util.List;
import java.util.Map;

public interface DecisionReader {
  String getDiagram(Long decisionDefinitionKey);

  DecisionDefinitionEntity getDecision(Long decisionDefinitionKey);

  Map<String, List<DecisionDefinitionEntity>> getDecisionsGrouped(DecisionRequestDto request);
}
