/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.springframework.context.ApplicationContext;

import java.time.OffsetDateTime;
import java.util.List;

public interface DecisionInstanceWriter {

  void importDecisionInstances(List<DecisionInstanceDto> decisionInstanceDtos);

  void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(final String decisionDefinitionKey,
                                                                        final OffsetDateTime evaluationDate);

  void reloadConfiguration(final ApplicationContext context);

}