/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.service;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.LongBounds;
import org.elasticsearch.search.aggregations.bucket.range.DateRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.previousOrSame;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToDateHistogramInterval;
import static org.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static org.camunda.optimize.service.es.filter.util.DateHistogramFilterUtil.createModelElementDateHistogramLimitingFilterFor;
import static org.camunda.optimize.service.es.filter.util.DateHistogramFilterUtil.extendBoundsAndCreateDecisionDateHistogramLimitingFilterFor;
import static org.camunda.optimize.service.es.filter.util.DateHistogramFilterUtil.extendBoundsAndCreateProcessDateHistogramLimitingFilterFor;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@RequiredArgsConstructor
@Component
public class DateAggregationService {

  private static final String DATE_AGGREGATION = "dateAggregation";

  private final DateTimeFormatter dateTimeFormatter;

  public Optional<AggregationBuilder> createProcessInstanceDateAggregation(final DateAggregationContext context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
        context,
        this::createFilterLimitedProcessDateHistogramWithSubAggregation
      );
    }
    return Optional.of(createFilterLimitedProcessDateHistogramWithSubAggregation(context));
  }

  public Optional<AggregationBuilder> createModelElementDateAggregation(final DateAggregationContext context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
        context,
        this::createFilterLimitedModelElementDateHistogramWithSubAggregation
      );
    }
    return Optional.of(createFilterLimitedModelElementDateHistogramWithSubAggregation(context));
  }

  public Optional<AggregationBuilder> createDateVariableAggregation(final DateAggregationContext context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
        context,
        this::createDateHistogramWithSubAggregation
      );
    }

    return Optional.of(createDateHistogramWithSubAggregation(context));
  }

  public Optional<AggregationBuilder> createDecisionEvaluationDateAggregation(final DateAggregationContext context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
        context,
        this::createFilterLimitedDecisionDateHistogramWithSubAggregation
      );
    }

    return Optional.of(createFilterLimitedDecisionDateHistogramWithSubAggregation(context));
  }

  public Optional<AggregationBuilder> createRunningDateAggregation(final DateAggregationContext context) {
    if (!context.getMinMaxStats().isMinValid()) {
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())
      && !context.getMinMaxStats().isValidRange()) {
      context.setAggregateByDateUnit(AggregateByDateUnit.MONTH);
    }

    return Optional.of(createRunningDateFilterAggregations(context));
  }

  public static Duration getDateHistogramIntervalDurationFromMinMax(final MinMaxStatDto minMaxStats) {
    final long intervalFromMinToMax =
      (long) (minMaxStats.getMax() - minMaxStats.getMin()) / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    // we need to ensure that the interval is > 1 since we create the range buckets based on this
    // interval and it will cause an endless loop if the interval is 0.
    return Duration.of(Math.max(intervalFromMinToMax, 1), ChronoUnit.MILLIS);
  }

  public Map<String, Aggregations> mapDateAggregationsToKeyAggregationMap(final Aggregations aggregations,
                                                                          final ZoneId timezone) {
    return mapDateAggregationsToKeyAggregationMap(aggregations, timezone, DATE_AGGREGATION);
  }

  public Map<String, Aggregations> mapDateAggregationsToKeyAggregationMap(final Aggregations aggregations,
                                                                          final ZoneId timezone,
                                                                          final String aggregationName) {
    final MultiBucketsAggregation agg = aggregations.get(aggregationName);

    Map<String, Aggregations> formattedKeyToBucketMap = new LinkedHashMap<>();
    for (MultiBucketsAggregation.Bucket entry : agg.getBuckets()) {
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

  private DateHistogramAggregationBuilder createDateHistogramAggregation(final DateAggregationContext context) {
    DateHistogramAggregationBuilder dateHistogramAggregationBuilder = AggregationBuilders
      .dateHistogram(context.getDateAggregationName().orElse(DATE_AGGREGATION))
      .order(BucketOrder.key(false))
      .field(context.getDateField())
      .calendarInterval(mapToDateHistogramInterval(context.getAggregateByDateUnit()))
      .format(OPTIMIZE_DATE_FORMAT)
      .timeZone(context.getTimezone());

    if (context.isExtendBoundsToMinMaxStats()
      && context.getMinMaxStats().isMaxValid()
      && context.getMinMaxStats().isMinValid()) {
      dateHistogramAggregationBuilder.extendedBounds(
        new LongBounds(Math.round(context.getMinMaxStats().getMin()), Math.round(context.getMinMaxStats().getMax()))
      );
    }

    return dateHistogramAggregationBuilder;
  }

  private DateHistogramAggregationBuilder createDateHistogramWithSubAggregation(final DateAggregationContext context) {
    final DateHistogramAggregationBuilder dateHistogramAggregationBuilder = AggregationBuilders
      .dateHistogram(context.getDateAggregationName().orElse(DATE_AGGREGATION))
      .order(BucketOrder.key(false))
      .field(context.getDateField())
      .calendarInterval(mapToDateHistogramInterval(context.getAggregateByDateUnit()))
      .format(OPTIMIZE_DATE_FORMAT)
      .timeZone(context.getTimezone());
    context.getSubAggregations().forEach(dateHistogramAggregationBuilder::subAggregation);
    return dateHistogramAggregationBuilder;
  }

  private AggregationBuilder createRunningDateFilterAggregations(final DateAggregationContext context) {
    final AggregateByDateUnit unit = context.getAggregateByDateUnit();
    final ZonedDateTime startOfFirstBucket = truncateToUnit(
      context.getEarliestDate(),
      unit
    );
    final ZonedDateTime endOfLastBucket = AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())
      ? context.getLatestDate()
      : truncateToUnit(context.getLatestDate(), context.getAggregateByDateUnit()).plus(
      1,
      mapToChronoUnit(unit)
    );
    final Duration automaticIntervalDuration = getDateHistogramIntervalDurationFromMinMax(context.getMinMaxStats());

    List<FiltersAggregator.KeyedFilter> filters = new ArrayList<>();
    for (ZonedDateTime currentBucketStart = startOfFirstBucket;
         currentBucketStart.isBefore(endOfLastBucket);
         currentBucketStart = getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration)) {
      // to use our correct date formatting we need to switch back to OffsetDateTime
      final String startAsString = dateTimeFormatter.format(currentBucketStart.toOffsetDateTime());
      final String endAsString = dateTimeFormatter.format(
        getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration).toOffsetDateTime());

      BoolQueryBuilder query = QueryBuilders.boolQuery()
        .must(QueryBuilders.rangeQuery(context.getDateField()).lt(endAsString))
        .must(
          QueryBuilders.boolQuery()
            .should(QueryBuilders.rangeQuery(context.getRunningDateReportEndDateField()).gte(startAsString))
            .should(QueryBuilders.boolQuery()
                      .mustNot(QueryBuilders.existsQuery(context.getRunningDateReportEndDateField())))
        );

      FiltersAggregator.KeyedFilter keyedFilter = new FiltersAggregator.KeyedFilter(startAsString, query);
      filters.add(keyedFilter);
    }

    final FiltersAggregationBuilder limitingFilterAggregation = AggregationBuilders
      .filters(FILTER_LIMITED_AGGREGATION, filters.toArray(new FiltersAggregator.KeyedFilter[]{}));
    context.getSubAggregations().forEach(limitingFilterAggregation::subAggregation);
    return limitingFilterAggregation;
  }

  private Optional<AggregationBuilder> createAutomaticIntervalAggregationOrFallbackToMonth(
    final DateAggregationContext context,
    final Function<DateAggregationContext, AggregationBuilder> defaultAggregationCreator) {
    final Optional<AggregationBuilder> automaticIntervalAggregation =
      createAutomaticIntervalAggregationWithSubAggregation(context);

    if (automaticIntervalAggregation.isPresent()) {
      final AggregationBuilder automaticIntervalAggregationValue = automaticIntervalAggregation.get();
      context.getSubAggregations().forEach(automaticIntervalAggregationValue::subAggregation);
      return Optional.of(
        wrapWithFilterLimitedParentAggregation(boolQuery().filter(matchAllQuery()), automaticIntervalAggregationValue)
      );
    }

    // automatic interval not possible, return default aggregation with unit month instead
    context.setAggregateByDateUnit(AggregateByDateUnit.MONTH);
    return Optional.of(defaultAggregationCreator.apply(context));
  }

  private Optional<AggregationBuilder> createAutomaticIntervalAggregationWithSubAggregation(final DateAggregationContext context) {
    if (!context.getMinMaxStats().isValidRange()) {
      return Optional.empty();
    }

    final ZonedDateTime min = context.getEarliestDate();
    final ZonedDateTime max = context.getLatestDate();

    final Duration intervalDuration = getDateHistogramIntervalDurationFromMinMax(context.getMinMaxStats());
    DateRangeAggregationBuilder rangeAgg = AggregationBuilders
      .dateRange(context.getDateAggregationName().orElse(DATE_AGGREGATION))
      .timeZone(min.getZone())
      .field(context.getDateField());
    ZonedDateTime start = min;

    do {
      // this is a do while loop to ensure there's always at least one bucket, even when min and max are equal
      ZonedDateTime nextStart = start.plus(intervalDuration);
      boolean isLast = nextStart.isAfter(max) || nextStart.isEqual(max);
      // plus 1 ms because the end of the range is exclusive yet we want to make sure max falls into the last bucket
      ZonedDateTime end = isLast ? nextStart.plus(1, ChronoUnit.MILLIS) : nextStart;

      RangeAggregator.Range range =
        new RangeAggregator.Range(
          dateTimeFormatter.format(start), // key that's being used
          dateTimeFormatter.format(start),
          dateTimeFormatter.format(end)
        );
      rangeAgg.addRange(range);
      start = nextStart;
    } while (start.isBefore(max));
    return Optional.of(rangeAgg);
  }

  private AggregationBuilder createFilterLimitedDecisionDateHistogramWithSubAggregation(final DateAggregationContext context) {
    final DateHistogramAggregationBuilder dateHistogramAggregation = createDateHistogramAggregation(context);

    final BoolQueryBuilder limitFilterQuery =
      extendBoundsAndCreateDecisionDateHistogramLimitingFilterFor(
        dateHistogramAggregation, context, dateTimeFormatter
      );

    context.getSubAggregations().forEach(dateHistogramAggregation::subAggregation);
    return wrapWithFilterLimitedParentAggregation(limitFilterQuery, dateHistogramAggregation);
  }

  private AggregationBuilder createFilterLimitedProcessDateHistogramWithSubAggregation(final DateAggregationContext context) {
    final DateHistogramAggregationBuilder dateHistogramAggregation = createDateHistogramAggregation(context);

    final BoolQueryBuilder limitFilterQuery = extendBoundsAndCreateProcessDateHistogramLimitingFilterFor(
      dateHistogramAggregation, context, dateTimeFormatter
    );

    context.getSubAggregations().forEach(dateHistogramAggregation::subAggregation);
    return wrapWithFilterLimitedParentAggregation(limitFilterQuery, dateHistogramAggregation);
  }

  private AggregationBuilder createFilterLimitedModelElementDateHistogramWithSubAggregation(final DateAggregationContext context) {
    final DateHistogramAggregationBuilder dateHistogramAggregation = createDateHistogramAggregation(context);

    final BoolQueryBuilder limitFilterQuery =
      createModelElementDateHistogramLimitingFilterFor(context, dateTimeFormatter);

    context.getSubAggregations().forEach(dateHistogramAggregation::subAggregation);
    return wrapWithFilterLimitedParentAggregation(limitFilterQuery, dateHistogramAggregation);
  }

  private ZonedDateTime getEndOfBucket(final ZonedDateTime startOfBucket,
                                       final AggregateByDateUnit unit,
                                       final Duration durationOfAutomaticInterval) {
    return AggregateByDateUnit.AUTOMATIC.equals(unit)
      ? startOfBucket.plus(durationOfAutomaticInterval)
      : startOfBucket.plus(1, mapToChronoUnit(unit));
  }


  private ZonedDateTime truncateToUnit(final ZonedDateTime dateToTruncate,
                                       final AggregateByDateUnit unit) {
    switch (unit) {
      case YEAR:
        return dateToTruncate
          .with(firstDayOfYear())
          .truncatedTo(ChronoUnit.DAYS);
      case MONTH:
        return dateToTruncate
          .with(firstDayOfMonth())
          .truncatedTo(ChronoUnit.DAYS);
      case WEEK:
        return dateToTruncate
          .with(previousOrSame(DayOfWeek.MONDAY))
          .truncatedTo(ChronoUnit.DAYS);
      case DAY:
      case HOUR:
      case MINUTE:
        return dateToTruncate
          .truncatedTo(mapToChronoUnit(unit));
      case AUTOMATIC:
        return dateToTruncate;
      default:
        throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }
}
