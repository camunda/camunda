/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.elasticsearch.index.query.QueryBuilder;

import static org.camunda.optimize.service.es.filter.util.ModelElementFilterQueryUtil.createModelElementAggregationFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@Slf4j
public abstract class ProcessGroupByFlowNodeDate extends AbstractProcessGroupByModelElementDate {

  private final DefinitionService definitionService;

  ProcessGroupByFlowNodeDate(final DateAggregationService dateAggregationService,
                             final MinMaxStatsService minMaxStatsService,
                             final DefinitionService definitionService) {
    super(dateAggregationService, minMaxStatsService);
    this.definitionService = definitionService;
  }

  @Override
  protected QueryBuilder getFilterQuery(final ExecutionContext<ProcessReportDataDto> context) {
    return createModelElementAggregationFilter(context.getReportData(), context.getFilterContext(), definitionService);
  }

  @Override
  protected QueryBuilder getModelElementTypeFilterQuery() {
    return matchAllQuery();
  }

  @Override
  protected String getPathToElementField() {
    return FLOW_NODE_INSTANCES;
  }

}
