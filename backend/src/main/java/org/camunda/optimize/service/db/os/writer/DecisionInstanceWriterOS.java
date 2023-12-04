/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.db.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DecisionInstanceWriterOS implements DecisionInstanceWriter {

  @Override
  public void importDecisionInstances(final List<DecisionInstanceDto> decisionInstanceDtos) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(final String decisionDefinitionKey,
                                                                               final OffsetDateTime evaluationDate) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    //todo will be handled in the OPT-7376
  }

}
