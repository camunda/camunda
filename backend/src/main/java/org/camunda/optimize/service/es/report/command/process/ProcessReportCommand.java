/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.index.query.BoolQueryBuilder;

public abstract class ProcessReportCommand<T extends ReportEvaluationResult>
  extends ReportCommand<T, SingleProcessReportDefinitionDto> {

  protected ProcessQueryFilterEnhancer queryFilterEnhancer;
  protected IntervalAggregationService intervalAggregationService;

  @Override
  public void beforeEvaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    intervalAggregationService = commandContext.getIntervalAggregationService();
    queryFilterEnhancer = (ProcessQueryFilterEnhancer) commandContext.getQueryFilterEnhancer();
  }

  protected BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto reportData) {
    final BoolQueryBuilder query = setupBaseQuery(reportData, new ProcessInstanceType());
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());
    return query;
  }

}
