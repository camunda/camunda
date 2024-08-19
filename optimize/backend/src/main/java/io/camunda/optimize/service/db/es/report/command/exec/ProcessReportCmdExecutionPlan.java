/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.exec;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.DistributedByPart;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.GroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.ViewPart;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;

public class ProcessReportCmdExecutionPlan<T>
    extends ReportCmdExecutionPlan<T, ProcessReportDataDto> {

  // Instance date filters should also reduce the total count (baseline) considered for report
  // evaluation
  private static final List<Class<? extends ProcessFilterDto<?>>> FILTERS_AFFECTING_BASELINE =
      List.of(
          InstanceStartDateFilterDto.class,
          InstanceEndDateFilterDto.class,
          FlowNodeStartDateFilterDto.class,
          FlowNodeEndDateFilterDto.class);
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessReportCmdExecutionPlan.class);

  protected final ProcessDefinitionReader processDefinitionReader;
  protected final ProcessQueryFilterEnhancer queryFilterEnhancer;

  public ProcessReportCmdExecutionPlan(
      final ViewPart<ProcessReportDataDto> viewPart,
      final GroupByPart<ProcessReportDataDto> groupByPart,
      final DistributedByPart<ProcessReportDataDto> distributedByPart,
      final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult,
      final DatabaseClient databaseClient,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessQueryFilterEnhancer queryFilterEnhancer) {
    super(viewPart, groupByPart, distributedByPart, mapToReportResult, databaseClient);
    this.processDefinitionReader = processDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  public BoolQueryBuilder setupBaseQuery(final ExecutionContext<ProcessReportDataDto> context) {
    final Map<String, List<ProcessFilterDto<?>>> dateFiltersByDefinition =
        context.getReportData().groupFiltersByDefinitionIdentifier();
    final BoolQueryBuilder multiDefinitionFilterQuery =
        buildDefinitionBaseQueryForFilters(context, dateFiltersByDefinition);

    queryFilterEnhancer.addFilterToQuery(
        multiDefinitionFilterQuery,
        Stream.concat(
                dateFiltersByDefinition
                    .getOrDefault(APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList())
                    .stream(),
                context.getReportData().getAdditionalFiltersForReportType().stream())
            .collect(Collectors.toList()),
        context.getFilterContext());
    return multiDefinitionFilterQuery;
  }

  @Override
  protected BoolQueryBuilder setupUnfilteredBaseQuery(
      final ExecutionContext<ProcessReportDataDto> context) {
    // Instance level date filters are also applied to the baseline so are included here
    final Map<String, List<ProcessFilterDto<?>>> instanceLevelDateFiltersByDefinitionKey =
        context.getReportData().groupFiltersByDefinitionIdentifier().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        entry.getValue().stream()
                            .filter(
                                filter ->
                                    filter.getFilterLevel() == FilterApplicationLevel.INSTANCE)
                            .filter(
                                filter -> FILTERS_AFFECTING_BASELINE.contains(filter.getClass()))
                            .collect(Collectors.toList())));
    final BoolQueryBuilder multiDefinitionFilterQuery =
        buildDefinitionBaseQueryForFilters(context, instanceLevelDateFiltersByDefinitionKey);

    queryFilterEnhancer.addFilterToQuery(
        multiDefinitionFilterQuery,
        instanceLevelDateFiltersByDefinitionKey.getOrDefault(
            APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList()),
        context.getFilterContext());
    return multiDefinitionFilterQuery;
  }

  @Override
  protected String[] getIndexNames(final ExecutionContext<ProcessReportDataDto> context) {
    if (context.getReportData().isManagementReport()) {
      getMultiIndexAlias();
    }
    return InstanceIndexUtil.getProcessInstanceIndexAliasNames(context.getReportData());
  }

  @Override
  protected String[] getMultiIndexAlias() {
    return new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
  }

  @Override
  protected Supplier<ProcessReportDataDto> getDataDtoSupplier() {
    return ProcessReportDataDto::new;
  }

  public Optional<MinMaxStatDto> getGroupByMinMaxStats(
      final ExecutionContext<ProcessReportDataDto> context) {
    return groupByPart.getMinMaxStats(context, setupBaseQuery(context));
  }

  private BoolQueryBuilder buildDefinitionBaseQueryForFilters(
      final ExecutionContext<ProcessReportDataDto> context,
      final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition) {
    // If the user has access to no definitions, management reports may contain no processes in its
    // data source so we exclude
    // all instances from the result
    if (context.getReportData().getDefinitions().isEmpty()
        && context.getReportData().isManagementReport()) {
      return boolQuery().mustNot(matchAllQuery());
    }
    final BoolQueryBuilder multiDefinitionFilterQuery = boolQuery().minimumShouldMatch(1);
    context
        .getReportData()
        .getDefinitions()
        .forEach(
            definitionDto -> {
              final BoolQueryBuilder definitionQuery = createDefinitionQuery(definitionDto);
              queryFilterEnhancer.addFilterToQuery(
                  definitionQuery,
                  filtersByDefinition.getOrDefault(
                      definitionDto.getIdentifier(), Collections.emptyList()),
                  context.getFilterContext());
              multiDefinitionFilterQuery.should(definitionQuery);
            });
    return multiDefinitionFilterQuery;
  }

  private BoolQueryBuilder createDefinitionQuery(final ReportDataDefinitionDto definitionDto) {
    return DefinitionQueryUtilES.createDefinitionQuery(
        definitionDto.getKey(),
        definitionDto.getVersions(),
        definitionDto.getTenantIds(),
        new ProcessInstanceIndexES(definitionDto.getKey()),
        processDefinitionReader::getLatestVersionToKey);
  }
}
