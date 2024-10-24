/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.db.es.builders.OptimizeBoolQueryBuilderES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.plan.AbstractExecutionPlanInterpreterES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractProcessExecutionPlanInterpreterES
    extends AbstractExecutionPlanInterpreterES<ProcessReportDataDto, ProcessExecutionPlan>
    implements ProcessExecutionPlanInterpreterES {
  // Instance date filters should also reduce the total count (baseline) considered for report
  // evaluation
  private static final List<Class<? extends ProcessFilterDto<?>>> FILTERS_AFFECTING_BASELINE =
      List.of(
          InstanceStartDateFilterDto.class,
          InstanceEndDateFilterDto.class,
          FlowNodeStartDateFilterDto.class,
          FlowNodeEndDateFilterDto.class);

  protected abstract ProcessDefinitionReader getProcessDefinitionReader();

  protected abstract ProcessQueryFilterEnhancerES getQueryFilterEnhancer();

  @Override
  public Optional<MinMaxStatDto> getGroupByMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (getGroupByInterpreter()
        instanceof final ProcessGroupByInterpreterES processGroupByInterpreter) {
      return processGroupByInterpreter.getMinMaxStats(
          context, Query.of(q -> q.bool(getBaseQueryBuilder(context).build())));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public BoolQuery.Builder getBaseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, List<ProcessFilterDto<?>>> dateFiltersByDefinition =
        context.getReportData().groupFiltersByDefinitionIdentifier();
    final BoolQuery.Builder multiDefinitionFilterQueryBuilder =
        buildDefinitionBaseQueryForFilters(context, dateFiltersByDefinition);

    getQueryFilterEnhancer()
        .addFilterToQuery(
            multiDefinitionFilterQueryBuilder,
            Stream.concat(
                    dateFiltersByDefinition
                        .getOrDefault(APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList())
                        .stream(),
                    context.getReportData().getAdditionalFiltersForReportType().stream())
                .collect(Collectors.toList()),
            context.getFilterContext());
    return multiDefinitionFilterQueryBuilder;
  }

  @Override
  protected BoolQuery.Builder setupUnfilteredBaseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
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
    final BoolQuery.Builder multiDefinitionFilterQuery =
        buildDefinitionBaseQueryForFilters(context, instanceLevelDateFiltersByDefinitionKey);

    getQueryFilterEnhancer()
        .addFilterToQuery(
            multiDefinitionFilterQuery,
            instanceLevelDateFiltersByDefinitionKey.getOrDefault(
                APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList()),
            context.getFilterContext());
    return multiDefinitionFilterQuery;
  }

  private BoolQuery.Builder buildDefinitionBaseQueryForFilters(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition) {
    // If the user has access to no definitions, management reports may contain no processes in its
    // data source so we exclude all instances from the result
    if (context.getReportData().getDefinitions().isEmpty()
        && context.getReportData().isManagementReport()) {
      final BoolQuery.Builder builder = new OptimizeBoolQueryBuilderES();
      builder.mustNot(m -> m.matchAll(i -> i));
      return builder;
    }
    final BoolQuery.Builder multiDefinitionFilterQuery =
        new OptimizeBoolQueryBuilderES().minimumShouldMatch("1");
    context
        .getReportData()
        .getDefinitions()
        .forEach(
            definitionDto -> {
              final BoolQuery.Builder definitionQueryBuilder = createDefinitionQuery(definitionDto);
              getQueryFilterEnhancer()
                  .addFilterToQuery(
                      definitionQueryBuilder,
                      filtersByDefinition.getOrDefault(
                          definitionDto.getIdentifier(), Collections.emptyList()),
                      context.getFilterContext());
              multiDefinitionFilterQuery.should(s -> s.bool(definitionQueryBuilder.build()));
            });
    return multiDefinitionFilterQuery;
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (context.getReportData().isManagementReport()) {
      getMultiIndexAlias();
    }
    return InstanceIndexUtil.getProcessInstanceIndexAliasNames(context.getReportData());
  }

  @Override
  protected String[] getMultiIndexAlias() {
    return new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
  }

  private BoolQuery.Builder createDefinitionQuery(final ReportDataDefinitionDto definitionDto) {
    return DefinitionQueryUtilES.createDefinitionQuery(
        definitionDto.getKey(),
        definitionDto.getVersions(),
        definitionDto.getTenantIds(),
        new ProcessInstanceIndexES(definitionDto.getKey()),
        getProcessDefinitionReader()::getLatestVersionToKey);
  }
}
