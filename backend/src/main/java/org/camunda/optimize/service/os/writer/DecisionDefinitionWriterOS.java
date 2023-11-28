/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DecisionDefinitionWriterOS implements DecisionDefinitionWriter {

  @Override
  public void importDecisionDefinitions(final List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void markDefinitionAsDeleted(final String definitionId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public boolean markRedeployedDefinitionsAsDeleted(final List<DecisionDefinitionOptimizeDto> importedDefinitions) {
    //todo will be handled in the OPT-7376
    return false;
  }

}
