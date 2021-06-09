/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.util.DefinitionQueryUtil;
import org.camunda.optimize.service.util.InstanceIndexUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

@Slf4j
public class ProcessReportCmdExecutionPlan<T> extends ReportCmdExecutionPlan<T, ProcessReportDataDto> {

  protected final ProcessDefinitionReader processDefinitionReader;
  protected final ProcessQueryFilterEnhancer queryFilterEnhancer;

  public ProcessReportCmdExecutionPlan(final ViewPart<ProcessReportDataDto> viewPart,
                                       final GroupByPart<ProcessReportDataDto> groupByPart,
                                       final DistributedByPart<ProcessReportDataDto> distributedByPart,
                                       final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult,
                                       final OptimizeElasticsearchClient esClient,
                                       final ProcessDefinitionReader processDefinitionReader,
                                       final ProcessQueryFilterEnhancer queryFilterEnhancer) {
    super(viewPart, groupByPart, distributedByPart, mapToReportResult, esClient);
    this.processDefinitionReader = processDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  public BoolQueryBuilder setupBaseQuery(final ExecutionContext<ProcessReportDataDto> context) {
    final BoolQueryBuilder boolQueryBuilder = setupUnfilteredBaseQuery(context.getReportData());
    queryFilterEnhancer.addFilterToQuery(
      boolQueryBuilder,
      getAllFilters(context.getReportData()),
      context.getTimezone(),
      context.getReportData().isUserTaskReport()
    );

    return boolQueryBuilder;
  }

  @Override
  protected BoolQueryBuilder setupUnfilteredBaseQuery(final ProcessReportDataDto reportData) {
    final BoolQueryBuilder multiDefinitionFilterQuery = boolQuery().minimumShouldMatch(1);
    reportData.getDefinitions().forEach(definitionDto -> multiDefinitionFilterQuery.should(
      DefinitionQueryUtil.createDefinitionQuery(
        definitionDto.getKey(),
        definitionDto.getVersions(),
        definitionDto.getTenantIds(),
        new ProcessInstanceIndex(definitionDto.getKey()),
        processDefinitionReader::getLatestVersionToKey
      )
    ));
    return multiDefinitionFilterQuery;
  }

  @Override
  protected String[] getIndexNames(final ExecutionContext<ProcessReportDataDto> context) {
    return InstanceIndexUtil.getProcessInstanceIndexAliasNames(context.getReportData());
  }

  @Override
  protected Supplier<ProcessReportDataDto> getDataDtoSupplier() {
    return ProcessReportDataDto::new;
  }

  public Optional<MinMaxStatDto> getGroupByMinMaxStats(final ExecutionContext<ProcessReportDataDto> context) {
    return groupByPart.getMinMaxStats(context, setupBaseQuery(context));
  }

  private List<ProcessFilterDto<?>> getAllFilters(final ProcessReportDataDto reportData) {
    List<ProcessFilterDto<?>> allFilters = new ArrayList<>();
    allFilters.addAll(reportData.getFilter());
    allFilters.addAll(reportData.getAdditionalFiltersForReportType());
    return allFilters;
  }
}
