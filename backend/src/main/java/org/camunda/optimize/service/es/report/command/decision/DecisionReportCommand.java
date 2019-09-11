/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.elasticsearch.index.query.BoolQueryBuilder;

public abstract class DecisionReportCommand<T extends ReportEvaluationResult>
  extends ReportCommand<T, SingleDecisionReportDefinitionDto> {
  protected DecisionQueryFilterEnhancer queryFilterEnhancer;
  protected IntervalAggregationService intervalAggregationService;
  private DecisionDefinitionReader decisionDefinitionReader;

  @Override
  protected void beforeEvaluate(final CommandContext commandContext) {
    intervalAggregationService = commandContext.getIntervalAggregationService();
    queryFilterEnhancer = (DecisionQueryFilterEnhancer) commandContext.getQueryFilterEnhancer();
    decisionDefinitionReader = commandContext.getDecisionDefinitionReader();
  }

  @Override
  protected T filterResultData(final CommandContext<SingleDecisionReportDefinitionDto> commandContext,
                               final T evaluationResult) {
    return super.filterResultData(commandContext, evaluationResult);
  }

  @Override
  protected String getLatestDefinitionVersionToKey(String definitionKey) {
    return decisionDefinitionReader.getLatestVersionToKey(definitionKey);
  }

  protected BoolQueryBuilder setupBaseQuery(final DecisionReportDataDto reportData) {
    final BoolQueryBuilder query = setupBaseQuery(reportData, new DecisionInstanceIndex());
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());
    return query;
  }

}
