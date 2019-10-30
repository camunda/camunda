/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.date.ProcessGroupByDate;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;

import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class ProcessGroupByDateReportCmdExecutionPlan extends ProcessReportCmdExecutionPlan<ReportMapResultDto> {

  private final ProcessGroupByDate processGroupByDatePart;

  private ProcessGroupByDateReportCmdExecutionPlan(final ViewPart<ProcessReportDataDto> viewPart,
                                                   final ProcessGroupByDate processGroupByDatePart,
                                                   final DistributedByPart<ProcessReportDataDto> distributedByPart,
                                                   final Function<CompositeCommandResult, ReportMapResultDto> mapToReportResult,
                                                   final OptimizeElasticsearchClient esClient,
                                                   final ProcessDefinitionReader processDefinitionReader,
                                                   final ProcessQueryFilterEnhancer queryFilterEnhancer) {
    super(
      viewPart,
      processGroupByDatePart,
      distributedByPart,
      mapToReportResult,
      esClient,
      processDefinitionReader,
      queryFilterEnhancer
    );
    this.processGroupByDatePart = processGroupByDatePart;
  }

  public ProcessGroupByDateReportCmdExecutionPlan(ProcessReportCmdExecutionPlan<ReportMapResultDto> plan) {
    this(
      plan.viewPart,
      (ProcessGroupByDate) plan.groupByPart,
      plan.distributedByPart,
      plan.mapToReportResult,
      plan.esClient,
      plan.processDefinitionReader,
      plan.queryFilterEnhancer
    );
  }

  public Optional<Stats> calculateGroupByDateRange(final ProcessReportDataDto reportData) {
    return processGroupByDatePart.calculateGroupByDateRange(reportData, setupBaseQuery(reportData));
  }
}
