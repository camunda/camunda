/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.index.query.BoolQueryBuilder;

public abstract class ProcessReportCommand<T extends ReportEvaluationResult>
  extends ReportCommand<T, SingleProcessReportDefinitionDto> {

  protected ProcessQueryFilterEnhancer queryFilterEnhancer;
  protected IntervalAggregationService intervalAggregationService;
  private ProcessDefinitionReader processDefinitionReader;

  @Override
  public void beforeEvaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    intervalAggregationService = commandContext.getIntervalAggregationService();
    queryFilterEnhancer = (ProcessQueryFilterEnhancer) commandContext.getQueryFilterEnhancer();
    processDefinitionReader = commandContext.getProcessDefinitionReader();
  }

  @Override
  protected String getLatestDefinitionVersionToKey(String definitionKey) {
    return processDefinitionReader.getLatestVersionToKey(definitionKey);
  }

  protected BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto reportData) {
    final BoolQueryBuilder query = setupBaseQuery(reportData, new ProcessInstanceIndex());
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());
    return query;
  }

}
