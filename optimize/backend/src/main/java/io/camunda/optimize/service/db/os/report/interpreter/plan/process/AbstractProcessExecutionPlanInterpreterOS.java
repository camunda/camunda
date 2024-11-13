/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.process;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.plan.AbstractExecutionPlanInterpreterOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.db.os.util.DefinitionQueryUtilOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public abstract class AbstractProcessExecutionPlanInterpreterOS
    extends AbstractExecutionPlanInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan>
    implements ProcessExecutionPlanInterpreterOS {
  protected abstract ProcessDefinitionReader getProcessDefinitionReader();

  protected abstract ProcessQueryFilterEnhancerOS getQueryFilterEnhancer();

  @Override
  public Optional<MinMaxStatDto> getGroupByMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (getGroupByInterpreter()
        instanceof final ProcessGroupByInterpreterOS processGroupByInterpreter) {
      return processGroupByInterpreter.getMinMaxStats(
          context, baseQueryBuilder(context).build().toQuery());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public BoolQuery.Builder baseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, List<ProcessFilterDto<?>>> dateFiltersByDefinition =
        context.getReportData().groupFiltersByDefinitionIdentifier();
    final List<ProcessFilterDto<?>> allDefinitionsFilters =
        dateFiltersByDefinition.getOrDefault(APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList());
    final List<ProcessFilterDto<?>> additionalFiltersStream =
        context.getReportData().getAdditionalFiltersForReportType();
    final List<ProcessFilterDto<?>> filters = new ArrayList<>(allDefinitionsFilters);
    filters.addAll(additionalFiltersStream);
    final List<Query> filterQueries =
        getQueryFilterEnhancer().filterQueries(filters, context.getFilterContext());
    return buildDefinitionBaseQueryForFilters(context, dateFiltersByDefinition, filterQueries);
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (context.isMultiIndexAlias() || context.getReportData().isManagementReport()) {
      return getMultiIndexAlias();
    }
    return InstanceIndexUtil.getProcessInstanceIndexAliasNames(context.getReportData());
  }

  @Override
  protected String[] getMultiIndexAlias() {
    return new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
  }

  @Override
  protected BoolQuery.Builder unfilteredBaseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    // Instance level date filters are also applied to the baseline so are included here
    final Map<String, List<ProcessFilterDto<?>>> instanceLevelDateFiltersByDefinitionKey =
        getInstanceLevelDateFiltersByDefinitionKey(context);
    final List<ProcessFilterDto<?>> filters =
        instanceLevelDateFiltersByDefinitionKey.getOrDefault(
            APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList());
    final List<Query> filterQueries =
        getQueryFilterEnhancer().filterQueries(filters, context.getFilterContext());
    return buildDefinitionBaseQueryForFilters(
        context, instanceLevelDateFiltersByDefinitionKey, filterQueries);
  }

  private BoolQuery.Builder buildDefinitionBaseQueryForFilters(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition,
      final List<Query> filterQueries) {
    // If the user has access to no definitions, management reports may contain no processes in its
    // data source so we exclude all instances from the result
    return context.getReportData().getDefinitions().isEmpty()
            && context.getReportData().isManagementReport()
        ? new BoolQuery.Builder().filter(not(matchAll()))
        : new BoolQuery.Builder()
            .minimumShouldMatch("1")
            .should(multiDefinitionFilterQueries(context, filtersByDefinition))
            .filter(filterQueries);
  }

  private List<Query> multiDefinitionFilterQueries(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition) {
    return context.getReportData().getDefinitions().stream()
        .map(
            definitionDto -> {
              final List<ProcessFilterDto<?>> filters =
                  filtersByDefinition.getOrDefault(definitionDto.getIdentifier(), List.of());
              return createDefinitionQuery(definitionDto, filters, context.getFilterContext());
            })
        .toList();
  }

  private Query createDefinitionQuery(
      final ReportDataDefinitionDto definitionDto,
      final List<ProcessFilterDto<?>> filters,
      final FilterContext filterContext) {
    final List<Query> filterQueries =
        getQueryFilterEnhancer().filterQueries(filters, filterContext);

    return DefinitionQueryUtilOS.createDefinitionQuery(
            definitionDto.getKey(),
            definitionDto.getVersions(),
            definitionDto.getTenantIds(),
            new ProcessInstanceIndexOS(definitionDto.getKey()),
            getProcessDefinitionReader()::getLatestVersionToKey)
        .filter(filterQueries)
        .build()
        .toQuery();
  }
}
