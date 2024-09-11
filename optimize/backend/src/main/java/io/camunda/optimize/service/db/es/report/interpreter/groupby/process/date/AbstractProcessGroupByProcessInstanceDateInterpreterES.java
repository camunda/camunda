/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtilES.unwrapFilterLimitedAggregations;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.groupby.ProcessGroupByProcessInstanceDateInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;

@RequiredArgsConstructor
public abstract class AbstractProcessGroupByProcessInstanceDateInterpreterES
    extends AbstractProcessGroupByInterpreterES {

  protected abstract ConfigurationService getConfigurationService();

  protected abstract DateAggregationServiceES getDateAggregationService();

  protected abstract MinMaxStatsServiceES getMinMaxStatsService();

  protected abstract ProcessQueryFilterEnhancerES getQueryFilterEnhancer();

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQueryBuilder baseQuery) {
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
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);
    baseQuery.must(existsQuery(getDateField()));
  }

  public abstract String getDateField();

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final AggregateByDateUnit unit = getGroupByDateUnit(context.getReportData());
    return createAggregation(searchSourceBuilder, context, unit);
  }

  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final AggregateByDateUnit unit) {
    final MinMaxStatDto stats = getMinMaxDateStats(context, searchSourceBuilder.query());

    final DateAggregationContextES dateAggContext =
        DateAggregationContextES.builder()
            .aggregateByDateUnit(unit)
            .dateField(getDateField())
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(
                getDistributedByInterpreter()
                    .createAggregations(context, searchSourceBuilder.query()))
            .processGroupByType(context.getReportData().getGroupBy().getType())
            .processFilters(context.getReportData().getFilter())
            .processQueryFilterEnhancer(getQueryFilterEnhancer())
            .filterContext(context.getFilterContext())
            .build();

    return getDateAggregationService()
        .createProcessInstanceDateAggregation(dateAggContext)
        .map(agg -> addSiblingAggregationIfRequired(context, searchSourceBuilder.query(), agg))
        .map(Collections::singletonList)
        .orElse(Collections.emptyList());
  }

  private MinMaxStatDto getMinMaxDateStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQuery) {
    return getMinMaxStatsService()
        .getMinMaxDateRange(context, baseQuery, getIndexNames(context), getDateField());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    ProcessGroupByProcessInstanceDateInterpreter.addQueryResult(
        processAggregations(response, response.getAggregations(), context),
        getDistributedByInterpreter().isKeyOfNumericType(context),
        result,
        context);
  }

  private List<GroupByResult> processAggregations(
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Aggregations> unwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(aggregations);
    if (unwrappedLimitedAggregations.isPresent()) {
      Map<String, Aggregations> keyToAggregationMap =
          getDateAggregationService()
              .mapDateAggregationsToKeyAggregationMap(
                  unwrappedLimitedAggregations.get(), context.getTimezone());
      // enrich context with complete set of distributed by keys
      getDistributedByInterpreter()
          .enrichContextWithAllExpectedDistributedByKeys(
              context, unwrappedLimitedAggregations.get());
      return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
    } else {
      return Collections.emptyList();
    }
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(
      final Map<String, Aggregations> keyToAggregationMap,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    getDistributedByInterpreter()
                        .retrieveResult(response, stringBucketEntry.getValue(), context)))
        .collect(Collectors.toList());
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

  private DistributedByType getDistributedByType(final ProcessReportDataDto processReportDataDto) {
    return processReportDataDto.getDistributedBy().getType();
  }

  private AggregationBuilder addSiblingAggregationIfRequired(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQueryBuilder,
      final AggregationBuilder aggregationBuilder) {
    // add sibling distributedBy aggregation to enrich context with all distributed by keys,
    // required for variable distribution
    if (DistributedByType.VARIABLE.equals(getDistributedByType(context.getReportData()))) {
      getDistributedByInterpreter()
          .createAggregations(context, baseQueryBuilder)
          .forEach(aggregationBuilder::subAggregation);
    }
    return aggregationBuilder;
  }
}
