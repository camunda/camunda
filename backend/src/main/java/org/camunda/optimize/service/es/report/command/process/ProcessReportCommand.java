/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class ProcessReportCommand<T extends ReportEvaluationResult>
  extends ReportCommand<T, SingleProcessReportDefinitionDto> {

  protected ProcessQueryFilterEnhancer queryFilterEnhancer;
  protected IntervalAggregationService intervalAggregationService;

  @Override
  public void beforeEvaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    intervalAggregationService = commandContext.getIntervalAggregationService();
    queryFilterEnhancer = (ProcessQueryFilterEnhancer) commandContext.getQueryFilterEnhancer();
  }

  protected BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto processReportData) {
    final String processDefinitionKey = processReportData.getProcessDefinitionKey();
    final String processDefinitionVersion = processReportData.getProcessDefinitionVersion();
    final BoolQueryBuilder query = boolQuery().must(termQuery(
      ProcessInstanceType.PROCESS_DEFINITION_KEY,
      processDefinitionKey
    ));
    if (!ReportConstants.ALL_VERSIONS.equalsIgnoreCase(processDefinitionVersion)) {
      query.must(termQuery(ProcessInstanceType.PROCESS_DEFINITION_VERSION, processDefinitionVersion));
    }
    queryFilterEnhancer.addFilterToQuery(query, processReportData.getFilter());
    return query;
  }

}
