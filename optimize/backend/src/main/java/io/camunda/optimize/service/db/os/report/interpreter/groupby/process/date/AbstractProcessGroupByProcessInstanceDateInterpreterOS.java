/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.unwrapFilterLimitedAggregations;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.service.db.os.report.context.DateAggregationContextOS;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.os.util.AggregateHelperOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.groupby.ProcessGroupByProcessInstanceDateInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.util.types.MapUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractProcessGroupByProcessInstanceDateInterpreterOS
    extends AbstractProcessGroupByInterpreterOS {

  public AbstractProcessGroupByProcessInstanceDateInterpreterOS() {}

  protected abstract ConfigurationService getConfigurationService();

  protected abstract DateAggregationServiceOS getDateAggregationService();

  protected abstract MinMaxStatsServiceOS getMinMaxStatsService();

  protected abstract ProcessQueryFilterEnhancerOS getQueryFilterEnhancer();

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    if (context.getReportData().getGroupBy().getValue()
        instanceof final DateGroupByValueDto groupByDate) {
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(getMinMaxDateStats(context, baseQuery));
      }
    }
    return Optional.empty();
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    super.adjustSearchRequest(searchRequestBuilder, baseQuery, context);
    searchRequestBuilder.query(and(baseQuery, exists(getDateField())));
  }

  public abstract String getDateField();

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final AggregateByDateUnit unit = getGroupByDateUnit(context.getReportData());
    return createAggregation(query, context, unit);
  }

  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final AggregateByDateUnit unit) {
    final MinMaxStatDto stats = getMinMaxDateStats(context, query);

    final DateAggregationContextOS dateAggContext =
        DateAggregationContextOS.builder()
            .aggregateByDateUnit(unit)
            .dateField(getDateField())
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(getDistributedByInterpreter().createAggregations(context, query))
            .processGroupByType(context.getReportData().getGroupBy().getType())
            .processFilters(context.getReportData().getFilter())
            .processQueryFilterEnhancer(getQueryFilterEnhancer())
            .filterContext(context.getFilterContext())
            .build();

    return getDateAggregationService()
        .createProcessInstanceDateAggregation(dateAggContext)
        .map(agg -> addSiblingAggregationIfRequired(context, query, agg))
        .map(MapUtil::createFromPair)
        .orElse(Map.of());
  }

  private MinMaxStatDto getMinMaxDateStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return getMinMaxStatsService()
        .getMinMaxDateRange(context, baseQuery, getIndexNames(context), getDateField());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregate> fixedAggregations =
        AggregateHelperOS.withNullValues(response.hits().total().value(), response.aggregations());
    ProcessGroupByProcessInstanceDateInterpreter.addQueryResult(
        processAggregations(response, fixedAggregations, context),
        getDistributedByInterpreter().isKeyOfNumericType(context),
        result,
        context);
  }

  private List<GroupByResult> processAggregations(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    return unwrapFilterLimitedAggregations(aggregations)
        .map(
            unwrappedLimitedAggregations -> {
              final Map<String, Map<String, Aggregate>> keyToAggregationMap =
                  getDateAggregationService()
                      .mapDateAggregationsToKeyAggregationMap(
                          unwrappedLimitedAggregations, context.getTimezone());
              // enrich context with complete set of distributed by keys
              getDistributedByInterpreter()
                  .enrichContextWithAllExpectedDistributedByKeys(
                      context, unwrappedLimitedAggregations);
              return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
            })
        .orElse(Collections.emptyList());
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(
      final Map<String, Map<String, Aggregate>> keyToAggregationMap,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    getDistributedByInterpreter()
                        .retrieveResult(response, stringBucketEntry.getValue(), context)))
        .toList();
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

  private DistributedByType getDistributedByType(final ProcessReportDataDto processReportDataDto) {
    return processReportDataDto.getDistributedBy().getType();
  }

  private Pair<String, Aggregation> addSiblingAggregationIfRequired(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery,
      final Pair<String, Aggregation> aggregation) {
    // add sibling distributedBy aggregation to enrich context with all distributed by keys,
    // required for variable distribution
    if (DistributedByType.VARIABLE.equals(getDistributedByType(context.getReportData()))) {
      final Map<String, Aggregation> subAggregations =
          MapUtil.combineUniqueMaps(
              aggregation.getValue().aggregations(),
              getDistributedByInterpreter().createAggregations(context, baseQuery));
      return Pair.of(
          aggregation.getKey(), withSubaggregations(aggregation.getValue(), subAggregations));
    }
    return aggregation;
  }
}
