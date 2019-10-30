/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.getExtendedBoundsFromDateFilters;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.limitFiltersToMaxBucketsForGroupByUnit;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.isResultComplete;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

public abstract class ProcessGroupByDate extends ProcessGroupByPart {

  protected final ConfigurationService configurationService;
  protected final IntervalAggregationService intervalAggregationService;

  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  protected ProcessGroupByDate(final ConfigurationService configurationService,
                               final IntervalAggregationService intervalAggregationService) {
    this.configurationService = configurationService;
    this.intervalAggregationService = intervalAggregationService;
  }

  public Optional<Stats> calculateGroupByDateRange(final ProcessReportDataDto reportData,
                                                   final BoolQueryBuilder baseQuery) {
    if (reportData.getGroupBy().getValue() instanceof DateGroupByValueDto) {
      DateGroupByValueDto groupByDate = (DateGroupByValueDto) reportData.getGroupBy().getValue();
      if (GroupByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        Stats minMaxStats = intervalAggregationService.getMinMaxStats(
          baseQuery,
          PROCESS_INSTANCE_INDEX_NAME,
          getDateField()
        );
        return Optional.of(minMaxStats);
      }
    }
    return Optional.empty();
  }

  @Override
  public void adjustBaseQuery(final BoolQueryBuilder baseQuery, final ProcessReportDataDto definitionData) {
    super.adjustBaseQuery(baseQuery, definitionData);
    baseQuery.must(existsQuery(getDateField()));
  }

  protected abstract ProcessGroupByDto<DateGroupByValueDto> getGroupByType();

  public abstract String getDateField();

  protected abstract List<DateFilterDataDto> getReportDateFilters(final ProcessReportDataDto reportData);

  protected abstract void addFiltersToQuery(final BoolQueryBuilder limitFilterQuery,
                                            final List<DateFilterDataDto> limitedFilters);

  protected abstract BoolQueryBuilder createDefaultLimitingFilter(final GroupByDateUnit unit,
                                                                  final QueryBuilder query,
                                                                  final ProcessReportDataDto reportData);


  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final GroupByDateUnit unit = getGroupByDateUnit(context.getReportData());
    return createAggregation(searchSourceBuilder, context, unit);
  }

  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context,
                                                    final GroupByDateUnit unit) {
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      return createAutomaticIntervalAggregation(searchSourceBuilder, context);
    }

    final DateHistogramInterval interval = intervalAggregationService.getDateHistogramInterval(unit);
    final DateHistogramAggregationBuilder dateHistogramAggregation = AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(getDateField())
      .dateHistogramInterval(interval)
      .timeZone(DateTimeZone.getDefault());

    final List<DateFilterDataDto> reportDateFilters = getReportDateFilters(context.getReportData());

    final BoolQueryBuilder limitFilterQuery;
    if (!reportDateFilters.isEmpty()) {
      final List<DateFilterDataDto> limitedFilters = limitFiltersToMaxBucketsForGroupByUnit(
        reportDateFilters, unit, configurationService.getEsAggregationBucketLimit()
      );

      getExtendedBoundsFromDateFilters(
        limitedFilters,
        DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat())
      ).ifPresent(dateHistogramAggregation::extendedBounds);

      limitFilterQuery = boolQuery();
      addFiltersToQuery(limitFilterQuery, limitedFilters);
    } else {
      limitFilterQuery = createDefaultLimitingFilter(unit, searchSourceBuilder.query(), context.getReportData());
    }

    return Collections.singletonList(
      wrapWithFilterLimitedParentAggregation(
        limitFilterQuery, dateHistogramAggregation.subAggregation(distributedByPart.createAggregation(context))
      )
    );
  }

  private List<AggregationBuilder> createAutomaticIntervalAggregation(final SearchSourceBuilder builder,
                                                                      final ExecutionContext<ProcessReportDataDto> context) {

    Optional<AggregationBuilder> automaticIntervalAggregation =
      intervalAggregationService.createIntervalAggregation(
        context.getDateIntervalRange(),
        builder.query(),
        PROCESS_INSTANCE_INDEX_NAME,
        getDateField()
      );

    return automaticIntervalAggregation.map(agg -> agg.subAggregation(distributedByPart.createAggregation(context)))
      .map(Collections::singletonList)
      .orElseGet(() -> createAggregation(builder, context, GroupByDateUnit.MONTH));
  }

  @Override
  public CompositeCommandResult retrieveQueryResult(final SearchResponse response,
                                                    final ProcessReportDataDto reportData) {
    CompositeCommandResult result = new CompositeCommandResult();
    result.setGroups(processAggregations(response.getAggregations(), reportData));
    result.setIsComplete(isResultComplete(response));
    return result;
  }

  private List<GroupByResult> processAggregations(final Aggregations aggregations,
                                                  final ProcessReportDataDto reportData) {
    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    List<GroupByResult> result = new ArrayList<>();
    if (unwrappedLimitedAggregations.isPresent()) {
      final Histogram agg = unwrappedLimitedAggregations.get().get(DATE_HISTOGRAM_AGGREGATION);

      for (Histogram.Bucket entry : agg.getBuckets()) {
        DateTime key = (DateTime) entry.getKey();
        String formattedDate = key.withZone(DateTimeZone.getDefault()).toString(OPTIMIZE_DATE_FORMAT);
        final List<DistributedByResult> distributions =
          distributedByPart.retrieveResult(entry.getAggregations(), reportData);
        result.add(GroupByResult.createGroupByResult(formattedDate, distributions));
      }
    } else {
      result = processAutomaticIntervalAggregations(aggregations, reportData);
    }
    return result;
  }

  private List<GroupByResult> processAutomaticIntervalAggregations(final Aggregations aggregations,
                                                                   final ProcessReportDataDto reportData) {
    return intervalAggregationService.mapIntervalAggregationsToKeyBucketMap(
      aggregations)
      .entrySet()
      .stream()
      .map(stringBucketEntry -> GroupByResult.createGroupByResult(
        stringBucketEntry.getKey(),
        distributedByPart.retrieveResult(stringBucketEntry.getValue().getAggregations(), reportData)
      ))
      .collect(Collectors.toList());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(getGroupByType());
  }

  private GroupByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }
}
