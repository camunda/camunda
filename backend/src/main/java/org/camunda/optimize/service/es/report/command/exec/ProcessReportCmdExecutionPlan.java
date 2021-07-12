/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
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

    final ProcessReportDataDto reportData = context.getReportData();
    final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition = groupFiltersByDefinitionIdentifier(reportData);
    final BoolQueryBuilder multiDefinitionFilterQuery = boolQuery().minimumShouldMatch(1);
    reportData.getDefinitions().forEach(definitionDto -> {
      final BoolQueryBuilder definitionQuery = createDefinitionQuery(definitionDto);
      queryFilterEnhancer.addFilterToQuery(
        definitionQuery,
        filtersByDefinition.getOrDefault(definitionDto.getIdentifier(), Collections.emptyList()),
        context.getFilterContext()
      );
      multiDefinitionFilterQuery.should(definitionQuery);
    });

    queryFilterEnhancer.addFilterToQuery(
      multiDefinitionFilterQuery,
      Stream.concat(
        filtersByDefinition.getOrDefault(APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList()).stream(),
        reportData.getAdditionalFiltersForReportType().stream()
      ).collect(Collectors.toList()),
      context.getFilterContext()
    );
    return multiDefinitionFilterQuery;
  }

  public Optional<MinMaxStatDto> getGroupByMinMaxStats(final ExecutionContext<ProcessReportDataDto> context) {
    return groupByPart.getMinMaxStats(context, setupBaseQuery(context));
  }

  @Override
  protected BoolQueryBuilder setupUnfilteredBaseQuery(final ProcessReportDataDto reportData) {
    final BoolQueryBuilder multiDefinitionFilterQuery = boolQuery().minimumShouldMatch(1);
    reportData.getDefinitions().forEach(definitionDto -> multiDefinitionFilterQuery.should(
      createDefinitionQuery(definitionDto)
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

  private BoolQueryBuilder createDefinitionQuery(final ReportDataDefinitionDto definitionDto) {
    return DefinitionQueryUtil.createDefinitionQuery(
      definitionDto.getKey(),
      definitionDto.getVersions(),
      definitionDto.getTenantIds(),
      new ProcessInstanceIndex(definitionDto.getKey()),
      processDefinitionReader::getLatestVersionToKey
    );
  }

  private Map<String, List<ProcessFilterDto<?>>> groupFiltersByDefinitionIdentifier(final ProcessReportDataDto reportData) {
    final Map<String, List<ProcessFilterDto<?>>> filterByDefinition = new HashMap<>();
    reportData.getFilter().forEach(filterDto -> filterDto.getAppliedTo().forEach(
      definitionIdentifier -> filterByDefinition.computeIfAbsent(definitionIdentifier, key -> new ArrayList<>())
        .add(filterDto)
    ));
    return filterByDefinition;
  }

}
