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
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_INSTANCE_PERCENTAGE_GROUP_BY_PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static java.util.stream.Collectors.toMap;

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
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractProcessExecutionPlanInterpreterOS
    extends AbstractExecutionPlanInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan>
    implements ProcessExecutionPlanInterpreterOS {

  private static final String VERSION_BASELINE_AGGREGATION = "versionBaselineAgg";

  protected abstract ProcessDefinitionReader getProcessDefinitionReader();

  protected abstract ProcessQueryFilterEnhancerOS getQueryFilterEnhancer();

  protected abstract ConfigurationService getConfigurationService();

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
  protected void populatePerGroupBaselineCounts(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final String[] indices) {
    if (context.getPlan() == PROCESS_INSTANCE_PERCENTAGE_GROUP_BY_PROCESS_DEFINITION_VERSION) {
      final BoolQuery.Builder baselineQuery = unfilteredBaseQueryBuilder(context);
      final SearchRequest.Builder requestBuilder =
          new SearchRequest.Builder()
              .index(Arrays.asList(indices))
              .query(baselineQuery.build().toQuery())
              .size(0)
              .aggregations(
                  VERSION_BASELINE_AGGREGATION,
                  a ->
                      a.terms(
                          t ->
                              t.field(PROCESS_DEFINITION_VERSION)
                                  .size(
                                      getConfigurationService()
                                          .getOpenSearchConfiguration()
                                          .getAggregationBucketLimit())
                                  .order(Map.of("_key", SortOrder.Asc))));
      final SearchResponse<?> response =
          getOsClient()
              .searchWithFixedAggregations(
                  requestBuilder, Object.class, "Could not retrieve per-version baseline counts");
      final StringTermsAggregate versionsAgg =
          response.aggregations().get(VERSION_BASELINE_AGGREGATION).sterms();
      final Map<String, Long> perVersionCounts =
          versionsAgg.buckets().array().stream().collect(toMap(b -> b.key(), b -> b.docCount()));
      context.setUnfilteredInstanceCountsByGroupKey(perVersionCounts);
    }
  }

  @Override
  protected BoolQuery.Builder unfilteredBaseQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, List<ProcessFilterDto<?>>> instanceLevelFiltersByDefinitionKey =
        buildBaselineFiltersByDefinition(context);
    final List<ProcessFilterDto<?>> filters =
        instanceLevelFiltersByDefinitionKey.getOrDefault(
            APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList());
    final List<Query> filterQueries =
        getQueryFilterEnhancer().filterQueries(filters, context.getFilterContext());
    return buildDefinitionBaseQueryForFilters(
        context, instanceLevelFiltersByDefinitionKey, filterQueries);
  }

  private BoolQuery.Builder buildDefinitionBaseQueryForFilters(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition,
      final List<Query> filterQueries) {
    if (context.getReportData().getDefinitions().isEmpty()) {
      if (context.getReportData().isManagementReport()) {
        // Management report with no accessible definitions: exclude all instances from the result
        return new BoolQuery.Builder().filter(not(matchAll()));
      }
      // No definitions: skip definition-scoped filtering and let other query filters apply
      return new BoolQuery.Builder().filter(filterQueries);
    }
    return new BoolQuery.Builder()
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
