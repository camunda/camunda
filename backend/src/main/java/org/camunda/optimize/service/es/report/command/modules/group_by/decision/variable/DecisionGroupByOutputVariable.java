/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.decision.variable;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByOutputVariableDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUTS;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionGroupByOutputVariable extends AbstractDecisionGroupByVariable {

  public DecisionGroupByOutputVariable(final ConfigurationService configurationService,
                                       final IntervalAggregationService intervalAggregationService,
                                       final OptimizeElasticsearchClient esClient) {
    super(configurationService, intervalAggregationService, esClient);
  }

  @Override
  protected DecisionGroupByVariableValueDto getVariableGroupByDto(final ExecutionContext<DecisionReportDataDto> context) {
    return ((DecisionGroupByOutputVariableDto) context.getReportData().getGroupBy()).getValue();
  }

  @Override
  protected DecisionGroupByDto<DecisionGroupByVariableValueDto> getDecisionGroupByVariableType() {
    return new DecisionGroupByOutputVariableDto();
  }

  @Override
  protected String getVariablePath() {
    return OUTPUTS;
  }
}
