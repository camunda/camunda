/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator;
import org.elasticsearch.search.aggregations.metrics.ParsedMax;
import org.elasticsearch.search.aggregations.metrics.ParsedMin;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.createUserTaskDateHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.extendBoundsAndCreateDecisionDateHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.extendBoundsAndCreateProcessDateHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@RequiredArgsConstructor
@Component
@Slf4j
public class DateAggregationService {

  private static final String MIN_AGGREGATION_FIRST_FIELD = "minValueOfFirstField";
  private static final String MIN_AGGREGATION_SECOND_FIELD = "minValueOfSecondField";
  private static final String MAX_AGGREGATION_FIRST_FIELD = "maxValueOfFirstField";
  private static final String MAX_AGGREGATION_SECOND_FIELD = "maxValueOfSecondField";
  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";
  public static final String RANGE_AGGREGATION = "rangeAggregation";

  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;
  private final ConfigurationService configurationService;

  public AggregationBuilder createFilterLimitedDecisionDateHistogramWithSubAggregation(final GroupByDateUnit unit,
                                                                                       final String dateField,
                                                                                       final ZoneId timezone,
                                                                                       final List<DecisionFilterDto<?>> decisionFilters,
                                                                                       final OffsetDateTime defaultFilterEndTime,
                                                                                       final DecisionQueryFilterEnhancer queryFilterEnhancer,
                                                                                       final AggregationBuilder distributedBySubAggregation) {
    final DateHistogramAggregationBuilder dateHistogramAggregation =
      createDateHistogramAggregation(unit, dateField, timezone);

    final BoolQueryBuilder limitFilterQuery = extendBoundsAndCreateDecisionDateHistogramBucketLimitingFilterFor(
      dateHistogramAggregation,
      decisionFilters,
      unit,
      configurationService.getEsAggregationBucketLimit(),
      defaultFilterEndTime,
      queryFilterEnhancer,
      timezone,
      dateTimeFormatter
    );

    return wrapWithFilterLimitedParentAggregation(
      limitFilterQuery,
      dateHistogramAggregation.subAggregation(distributedBySubAggregation)
    );
  }

  public AggregationBuilder createFilterLimitedProcessDateHistogramWithSubAggregation(final GroupByDateUnit unit,
                                                                                      final String dateField,
                                                                                      final ZoneId timezone,
                                                                                      final ProcessGroupByType groupByDateType,
                                                                                      final List<ProcessFilterDto<?>> processFilters,
                                                                                      final OffsetDateTime defaultFilterEndTime,
                                                                                      final ProcessQueryFilterEnhancer queryFilterEnhancer,
                                                                                      final AggregationBuilder distributedBySubAggregation) {
    final DateHistogramAggregationBuilder dateHistogramAggregation =
      createDateHistogramAggregation(unit, dateField, timezone);

    final BoolQueryBuilder limitFilterQuery = extendBoundsAndCreateProcessDateHistogramBucketLimitingFilterFor(
      groupByDateType,
      dateHistogramAggregation,
      processFilters,
      unit,
      configurationService.getEsAggregationBucketLimit(),
      defaultFilterEndTime,
      queryFilterEnhancer,
      timezone,
      dateTimeFormatter
    );

    return wrapWithFilterLimitedParentAggregation(
      limitFilterQuery,
      dateHistogramAggregation.subAggregation(distributedBySubAggregation)
    );
  }

  public AggregationBuilder createFilterLimitedUserTaskDateHistogramWithSubAggregation(final GroupByDateUnit unit,
                                                                                       final String dateField,
                                                                                       final ZoneId timezone,
                                                                                       final OffsetDateTime latestDate,
                                                                                       final AggregationBuilder distributedBySubAggregation) {
    final DateHistogramAggregationBuilder dateHistogramAggregation =
      createDateHistogramAggregation(unit, dateField, timezone);

    final BoolQueryBuilder limitFilterQuery =
      createUserTaskDateHistogramBucketLimitingFilterFor(
        unit,
        configurationService.getEsAggregationBucketLimit(),
        latestDate,
        dateField,
        timezone,
        dateTimeFormatter
      );

    return wrapWithFilterLimitedParentAggregation(
      limitFilterQuery,
      dateHistogramAggregation.subAggregation(distributedBySubAggregation)
    );
  }

  public DateHistogramAggregationBuilder createDateHistogramAggregation(final GroupByDateUnit unit,
                                                                        final String dateField,
                                                                        final ZoneId timezone) {
    return createDateHistogramAggregation(unit, dateField, DATE_HISTOGRAM_AGGREGATION, timezone);
  }

  public DateHistogramAggregationBuilder createDateHistogramAggregation(final GroupByDateUnit unit,
                                                                        final String dateField,
                                                                        final String histogramAggregationName,
                                                                        final ZoneId timezone) {
    final DateHistogramInterval interval = mapOptimizeDateUnitToElasticsearchInterval(unit);
    return AggregationBuilders
      .dateHistogram(histogramAggregationName)
      .order(BucketOrder.key(false))
      .field(dateField)
      .dateHistogramInterval(interval)
      .format(OPTIMIZE_DATE_FORMAT)
      .timeZone(timezone);
  }

  public Optional<AggregationBuilder> createAutomaticIntervalAggregation(final QueryBuilder query,
                                                                         final String indexName,
                                                                         final String field,
                                                                         final ZoneId timezone) {
    return createAutomaticIntervalAggregation(
      getCrossFieldMinMaxStats(query, indexName, field),
      field,
      timezone
    );
  }

  public Optional<AggregationBuilder> createAutomaticIntervalAggregation(final MinMaxStatDto stats,
                                                                         final String field,
                                                                         final ZoneId timezone) {
    if (stats.isValidRange()) {
      OffsetDateTime min = OffsetDateTime.parse(stats.getMinAsString(), dateTimeFormatter);
      OffsetDateTime max = OffsetDateTime.parse(stats.getMaxAsString(), dateTimeFormatter);
      return Optional.of(createIntervalAggregationFromGivenRange(field, timezone, min, max));
    } else {
      return Optional.empty();
    }
  }

  public AggregationBuilder createIntervalAggregationFromGivenRange(final String field,
                                                                    final ZoneId timezone,
                                                                    final OffsetDateTime min,
                                                                    final OffsetDateTime max) {
    final Duration intervalDuration = getDateHistogramIntervalDurationFromMinMax(min, max);
    RangeAggregationBuilder rangeAgg = AggregationBuilders
      .range(RANGE_AGGREGATION)
      .field(field);
    OffsetDateTime start = min;
    int bucketCount = 0;

    do {
      if (bucketCount >= configurationService.getEsAggregationBucketLimit()) {
        break;
      }
      // this is a do while loop to ensure there's always at least one bucket, even when min and max are equal
      OffsetDateTime nextStart = start.plus(intervalDuration);
      boolean isLast = nextStart.isAfter(max) || nextStart.isEqual(max);
      // plus 1 ms because the end of the range is exclusive yet we want to make sure max falls into the last bucket
      OffsetDateTime end = isLast ? nextStart.plus(1, ChronoUnit.MILLIS) : nextStart;

      RangeAggregator.Range range =
        new RangeAggregator.Range(
          dateTimeFormatter.format(start.atZoneSameInstant(timezone)), // key that's being used
          dateTimeFormatter.format(start.atZoneSameInstant(timezone)),
          dateTimeFormatter.format(end.atZoneSameInstant(timezone))
        );
      rangeAgg.addRange(range);
      start = nextStart;
      bucketCount++;
    } while (start.isBefore(max));
    return rangeAgg;
  }

  public MinMaxStatDto getMinMaxDateRange(final ExecutionContext<ProcessReportDataDto> context,
                                          final QueryBuilder query,
                                          final String indexName,
                                          final String field) {
    final boolean combinedReportRangeProvided = context.getDateIntervalRange().isPresent();
    if (combinedReportRangeProvided) {
      return new MinMaxStatDto(
        context.getDateIntervalRange().get().getMinimum().toEpochSecond(),
        context.getDateIntervalRange().get().getMaximum().toEpochSecond(),
        context.getDateIntervalRange().get().getMinimum().format(dateTimeFormatter),
        context.getDateIntervalRange().get().getMaximum().format(dateTimeFormatter)
      );
    } else {
      return getCrossFieldMinMaxStats(
        query,
        indexName,
        field
      );
    }
  }

  public MinMaxStatDto getCrossFieldMinMaxStats(final QueryBuilder query,
                                                final String indexName,
                                                final String minMaxField) {
    return getCrossFieldMinMaxStats(query, indexName, minMaxField, minMaxField);
  }

  public MinMaxStatDto getCrossFieldMinMaxStats(final QueryBuilder query,
                                                final String indexName,
                                                final String firstField,
                                                final String secondField) {
    AggregationBuilder minAgg1 = AggregationBuilders
      .min(MIN_AGGREGATION_FIRST_FIELD)
      .field(firstField);
    AggregationBuilder minAgg2 = AggregationBuilders
      .min(MIN_AGGREGATION_SECOND_FIELD)
      .field(secondField);
    AggregationBuilder maxAgg1 = AggregationBuilders
      .max(MAX_AGGREGATION_FIRST_FIELD)
      .field(firstField);
    AggregationBuilder maxAgg2 = AggregationBuilders
      .max(MAX_AGGREGATION_SECOND_FIELD)
      .field(secondField);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(minAgg1)
      .aggregation(minAgg2)
      .aggregation(maxAgg1)
      .aggregation(maxAgg2)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(indexName).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Could not retrieve stats for firstField %s and secondField %s on index %s",
        firstField,
        secondField,
        indexName
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return mapStatsAggregationToStatDto(response);
  }

  public DateHistogramInterval mapOptimizeDateUnitToElasticsearchInterval(GroupByDateUnit interval) {
    switch (interval) {
      case YEAR:
        return DateHistogramInterval.YEAR;
      case MONTH:
        return DateHistogramInterval.MONTH;
      case WEEK:
        return DateHistogramInterval.WEEK;
      case DAY:
        return DateHistogramInterval.DAY;
      case HOUR:
        return DateHistogramInterval.HOUR;
      case MINUTE:
        return DateHistogramInterval.MINUTE;
      default:
        final String errorMessage =
          String.format("Unknown date interval [%s] for creating a histogram aggregation.", interval);
        log.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public static Duration getDateHistogramIntervalDurationFromMinMax(OffsetDateTime min, OffsetDateTime max) {
    long minInMs = min.toInstant().toEpochMilli();
    long maxInMs = max.toInstant().toEpochMilli();
    final long intervalFromMinToMax = (maxInMs - minInMs) / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    // we need to ensure that the interval is > 1 since we create the range buckets based on this
    // interval and it will cause an endless loop if the interval is 0.
    return Duration.of(Math.max(intervalFromMinToMax, 1), ChronoUnit.MILLIS);
  }

  public Map<String, Aggregations> mapRangeAggregationsToKeyAggregationMap(final Aggregations aggregations,
                                                                           final ZoneId timezone) {
    Range agg = aggregations.get(RANGE_AGGREGATION);

    Map<String, Aggregations> formattedKeyToBucketMap = new LinkedHashMap<>();
    for (Range.Bucket entry : agg.getBuckets()) {
      String formattedDate = formatToCorrectTimezone(entry.getKeyAsString(), timezone, dateTimeFormatter);
      formattedKeyToBucketMap.put(formattedDate, entry.getAggregations());
    }
    // sort in descending order
    formattedKeyToBucketMap = formattedKeyToBucketMap.entrySet().stream()
      .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
        throw new IllegalStateException(String.format("Duplicate key %s", u));
      }, LinkedHashMap::new));
    return formattedKeyToBucketMap;
  }

  public Map<String, Aggregations> mapHistogramAggregationsToKeyAggregationMap(final Aggregations aggregations,
                                                                               final ZoneId timezone) {
    final Histogram agg = aggregations.get(DATE_HISTOGRAM_AGGREGATION);

    Map<String, Aggregations> formattedKeyToBucketMap = new LinkedHashMap<>();
    for (Histogram.Bucket entry : agg.getBuckets()) {
      String formattedDate = formatToCorrectTimezone(entry.getKeyAsString(), timezone, dateTimeFormatter);
      formattedKeyToBucketMap.put(formattedDate, entry.getAggregations());
    }
    // sort in descending order
    formattedKeyToBucketMap = formattedKeyToBucketMap.entrySet().stream()
      .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
        throw new IllegalStateException(String.format("Duplicate key %s", u));
      }, LinkedHashMap::new));
    return formattedKeyToBucketMap;
  }

  private MinMaxStatDto mapStatsAggregationToStatDto(final SearchResponse response) {
    final ParsedMin minAgg1 = response.getAggregations().get(MIN_AGGREGATION_FIRST_FIELD);
    final ParsedMin minAgg2 = response.getAggregations().get(MIN_AGGREGATION_SECOND_FIELD);
    final ParsedMax maxAgg1 = response.getAggregations().get(MAX_AGGREGATION_FIRST_FIELD);
    final ParsedMax maxAgg2 = response.getAggregations().get(MAX_AGGREGATION_SECOND_FIELD);

    final ParsedMin minAgg = minAgg1.getValue() < minAgg2.getValue()
      ? minAgg1
      : minAgg2;
    final ParsedMax maxAgg = maxAgg1.getValue() > maxAgg2.getValue()
      ? maxAgg1
      : maxAgg2;

    return new MinMaxStatDto(
      minAgg.getValue(),
      maxAgg.getValue(),
      minAgg.getValueAsString(),
      maxAgg.getValueAsString()
    );
  }
}
