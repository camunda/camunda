/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process.date;

import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import io.camunda.optimize.service.db.es.report.MinMaxStatsService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.process.ProcessGroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.db.es.report.command.service.DateAggregationService;
import io.camunda.optimize.service.db.es.report.command.util.DateAggregationContext;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public abstract class AbstractProcessGroupByProcessInstanceDate extends ProcessGroupByPart {

  protected final ConfigurationService configurationService;
  protected final DateAggregationService dateAggregationService;
  protected final MinMaxStatsService minMaxStatsService;
  protected final ProcessQueryFilterEnhancer queryFilterEnhancer;

  public AbstractProcessGroupByProcessInstanceDate(
      final ConfigurationService configurationService,
      final DateAggregationService dateAggregationService,
      final MinMaxStatsService minMaxStatsService,
      final ProcessQueryFilterEnhancer queryFilterEnhancer) {
    this.configurationService = configurationService;
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<ProcessReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);
    baseQuery.must(existsQuery(getDateField()));
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto> context) {
    final AggregateByDateUnit unit = getGroupByDateUnit(context.getReportData());
    return createAggregation(searchSourceBuilder, context, unit);
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto> context, final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      final DateGroupByValueDto groupByDate =
          (DateGroupByValueDto) context.getReportData().getGroupBy().getValue();
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(getMinMaxDateStats(context, baseQuery));
      }
    }
    return Optional.empty();
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto> context) {
    result.setGroups(processAggregations(response, response.getAggregations(), context));
    result.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC)));
    result.setGroupByKeyOfNumericType(false);
    result.setDistributedByKeyOfNumericType(distributedByPart.isKeyOfNumericType(context));
    final ProcessReportDataDto reportData = context.getReportData();
    // We sort by label for management report because keys change on every request
    if (reportData.isManagementReport()) {
      result.setDistributedBySorting(
          new ReportSortingDto(ReportSortingDto.SORT_BY_LABEL, SortOrder.ASC));
    }
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto reportData) {
    reportData.setGroupBy(getGroupByType());
  }

  protected abstract ProcessGroupByDto<DateGroupByValueDto> getGroupByType();

  public abstract String getDateField();

  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto> context,
      final AggregateByDateUnit unit) {
    // set baseQuery in context for distribution by variable minMaxStat calculation
    context.setDistributedByMinMaxBaseQuery(searchSourceBuilder.query());

    final MinMaxStatDto stats = getMinMaxDateStats(context, searchSourceBuilder.query());

    final DateAggregationContext dateAggContext =
        DateAggregationContext.builder()
            .aggregateByDateUnit(unit)
            .dateField(getDateField())
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(distributedByPart.createAggregations(context))
            .processGroupByType(getGroupByType().getType())
            .processFilters(context.getReportData().getFilter())
            .processQueryFilterEnhancer(queryFilterEnhancer)
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService
        .createProcessInstanceDateAggregation(dateAggContext)
        .map(agg -> addSiblingAggregationIfRequired(context, agg))
        .map(Collections::singletonList)
        .orElse(Collections.emptyList());
  }

  private MinMaxStatDto getMinMaxDateStats(
      final ExecutionContext<ProcessReportDataDto> context, final QueryBuilder baseQuery) {
    return minMaxStatsService.getMinMaxDateRange(
        context, baseQuery, getIndexNames(context), getDateField());
  }

  private List<GroupByResult> processAggregations(
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<ProcessReportDataDto> context) {
    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Aggregations> unwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(aggregations);
    if (unwrappedLimitedAggregations.isPresent()) {
      final Map<String, Aggregations> keyToAggregationMap =
          dateAggregationService.mapDateAggregationsToKeyAggregationMap(
              unwrappedLimitedAggregations.get(), context.getTimezone());
      // enrich context with complete set of distributed by keys
      distributedByPart.enrichContextWithAllExpectedDistributedByKeys(
          context, unwrappedLimitedAggregations.get());
      return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
    } else {
      return Collections.emptyList();
    }
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(
      final Map<String, Aggregations> keyToAggregationMap,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    distributedByPart.retrieveResult(
                        response, stringBucketEntry.getValue(), context)))
        .collect(Collectors.toList());
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

  private DistributedByType getDistributedByType(final ProcessReportDataDto processReportDataDto) {
    return processReportDataDto.getDistributedBy().getType();
  }

  private AggregationBuilder addSiblingAggregationIfRequired(
      final ExecutionContext<ProcessReportDataDto> context,
      final AggregationBuilder aggregationBuilder) {
    // add sibling distributedBy aggregation to enrich context with all distributed by keys,
    // required for variable distribution
    if (DistributedByType.VARIABLE.equals(getDistributedByType(context.getReportData()))) {
      distributedByPart.createAggregations(context).forEach(aggregationBuilder::subAggregation);
    }
    return aggregationBuilder;
  }
}
