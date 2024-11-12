/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.service;

import static io.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.fieldDateMath;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.filtersAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.filter;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.gte;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.lt;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.or;
import static io.camunda.optimize.service.db.os.report.filter.util.DateHistogramFilterUtilOS.createDecisionDateHistogramLimitingFilter;
import static io.camunda.optimize.service.db.os.report.filter.util.DateHistogramFilterUtilOS.createFilterBoolQueryBuilder;
import static io.camunda.optimize.service.db.os.report.filter.util.DateHistogramFilterUtilOS.createModelElementDateHistogramLimitingFilterQueryFor;
import static io.camunda.optimize.service.db.os.report.filter.util.DateHistogramFilterUtilOS.extendBounds;
import static io.camunda.optimize.service.db.os.report.filter.util.DateHistogramFilterUtilOS.getExtendedBoundsFromDateFilters;
import static io.camunda.optimize.service.db.os.report.interpreter.util.AggregateByDateUnitMapperOS.mapToCalendarInterval;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.wrapWithFilterLimitedParentAggregation;
import static io.camunda.optimize.service.db.report.interpreter.util.AggregateByDateUnitMapper.mapToChronoUnit;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.service.db.os.report.context.DateAggregationContextOS;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.report.service.DateAggregationService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.DateRangeExpression;
import org.opensearch.client.opensearch._types.aggregations.HistogramOrder;
import org.opensearch.client.opensearch._types.aggregations.RangeBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DateAggregationServiceOS extends DateAggregationService {

  private static final String DATE_AGGREGATION = "dateAggregation";

  private final DateTimeFormatter dateTimeFormatter;

  public DateAggregationServiceOS(final DateTimeFormatter dateTimeFormatter) {
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public Optional<Pair<String, Aggregation>> createProcessInstanceDateAggregation(
      final DateAggregationContextOS context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return Optional.of(
          createAutomaticIntervalAggregationOrFallbackToMonth(
              context, this::createFilterLimitedProcessDateHistogramWithSubAggregation));
    }
    return Optional.of(createFilterLimitedProcessDateHistogramWithSubAggregation(context));
  }

  public Optional<Pair<String, Aggregation>> createModelElementDateAggregation(
      final DateAggregationContextOS context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    final Pair<String, Aggregation> agg =
        AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())
            ? createAutomaticIntervalAggregationOrFallbackToMonth(
                context, this::createFilterLimitedModelElementDateHistogramWithSubAggregation)
            : createFilterLimitedModelElementDateHistogramWithSubAggregation(context);

    return Optional.of(agg);
  }

  public Optional<Pair<String, Aggregation>> createDateVariableAggregation(
      final DateAggregationContextOS context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return Optional.of(
          createAutomaticIntervalAggregationOrFallbackToMonth(
              context, this::createDateHistogramWithSubAggregation));
    }

    return Optional.of(createDateHistogramWithSubAggregation(context));
  }

  public Optional<Pair<String, Aggregation>> createDecisionEvaluationDateAggregation(
      final DateAggregationContextOS context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return Optional.ofNullable(
          createAutomaticIntervalAggregationOrFallbackToMonth(
              context, this::createFilterLimitedDecisionDateHistogramWithSubAggregation));
    }

    return Optional.of(createFilterLimitedDecisionDateHistogramWithSubAggregation(context));
  }

  public Optional<Pair<String, Aggregation>> createRunningDateAggregation(
      final DateAggregationContextOS context) {
    if (!context.getMinMaxStats().isMinValid()) {
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())
        && !context.getMinMaxStats().isValidRange()) {
      context.setAggregateByDateUnit(AggregateByDateUnit.MONTH);
    }

    return Optional.of(createRunningDateFilterAggregations(context));
  }

  public Map<String, Map<String, Aggregate>> mapDateAggregationsToKeyAggregationMap(
      final Map<String, Aggregate> aggregations, final ZoneId timezone) {
    return mapDateAggregationsToKeyAggregationMap(aggregations, timezone, DATE_AGGREGATION);
  }

  private Map<String, Map<String, Aggregate>> multiBucketAggregation(final Aggregate aggregate) {
    if (aggregate.isDateHistogram()) {
      return aggregate.dateHistogram().buckets().array().stream()
          .collect(Collectors.toMap(DateHistogramBucket::key, DateHistogramBucket::aggregations));
    } else if (aggregate.isDateRange()) {
      return aggregate.dateRange().buckets().array().stream()
          .collect(Collectors.toMap(RangeBucket::key, RangeBucket::aggregations));
    } else {
      throw new UnsupportedOperationException(
          "Unsupported multi bucket aggregation type " + aggregate._kind().name());
    }
  }

  private String formatToCorrectTimezoneWithFallback(
      String dateTime, final ZoneId timezone, final DateTimeFormatter formatter) {
    try {
      return formatToCorrectTimezone(dateTime, timezone, formatter);
    } catch (final Exception e) {
      try {
        dateTime =
            java.time.Instant.ofEpochMilli(Long.parseLong(dateTime))
                .atZone(ZoneId.of("UTC"))
                .format(formatter);
        return formatToCorrectTimezone(dateTime, timezone, formatter);
      } catch (final Exception e2) {
        throw new OptimizeRuntimeException("Failed to parse date time: " + dateTime, e2);
      }
    }
  }

  public Map<String, Map<String, Aggregate>> mapDateAggregationsToKeyAggregationMap(
      final Map<String, Aggregate> aggregations,
      final ZoneId timezone,
      final String aggregationName) {
    return multiBucketAggregation(aggregations.get(aggregationName)).entrySet().stream()
        .map(
            entry ->
                Pair.of(
                    formatToCorrectTimezoneWithFallback(
                        entry.getKey(), timezone, dateTimeFormatter),
                    entry.getValue()))
        .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (u, v) -> {
                  throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                LinkedHashMap::new));
  }

  private Pair<String, Aggregation> createDateHistogramAggregation(
      final DateAggregationContextOS context,
      final Consumer<DateHistogramAggregation.Builder>
          dateHistogramAggregationBuilderModification) {
    final DateHistogramAggregation.Builder dateHistogramAggregationBuilder =
        new DateHistogramAggregation.Builder()
            .order(b -> b.key(SortOrder.Desc))
            .field(context.getDateField())
            .calendarInterval(mapToCalendarInterval(context.getAggregateByDateUnit()))
            .format(OPTIMIZE_DATE_FORMAT)
            .timeZone(context.getTimezone().getDisplayName(TextStyle.SHORT, Locale.US));

    if (context.isExtendBoundsToMinMaxStats()
        && context.getMinMaxStats().isMaxValid()
        && context.getMinMaxStats().isMinValid()) {
      dateHistogramAggregationBuilder.extendedBounds(
          b ->
              b.min(fieldDateMath(context.getMinMaxStats().getMin()))
                  .max(fieldDateMath(context.getMinMaxStats().getMax())));
    }

    dateHistogramAggregationBuilderModification.accept(dateHistogramAggregationBuilder);

    return Pair.of(
        context.getDateAggregationName().orElse(DATE_AGGREGATION),
        new Aggregation.Builder()
            .dateHistogram(dateHistogramAggregationBuilder.build())
            .aggregations(context.getSubAggregations())
            .build());
  }

  private Pair<String, Aggregation> createDateHistogramWithSubAggregation(
      final DateAggregationContextOS context) {
    return Pair.of(
        context.getDateAggregationName().orElse(DATE_AGGREGATION),
        new Aggregation.Builder()
            .dateHistogram(
                b ->
                    b.order(HistogramOrder.of(b1 -> b1.key(SortOrder.Desc)))
                        .field(context.getDateField())
                        .calendarInterval(mapToCalendarInterval(context.getAggregateByDateUnit()))
                        .format(OPTIMIZE_DATE_FORMAT)
                        .timeZone(context.getTimezone().getDisplayName(TextStyle.SHORT, Locale.US)))
            .aggregations(context.getSubAggregations())
            .build());
  }

  private Pair<String, Aggregation> createRunningDateFilterAggregations(
      final DateAggregationContextOS context) {
    final AggregateByDateUnit unit = context.getAggregateByDateUnit();
    final ZonedDateTime startOfFirstBucket = truncateToUnit(context.getEarliestDate(), unit);
    final ZonedDateTime endOfLastBucket =
        AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())
            ? context.getLatestDate()
            : truncateToUnit(context.getLatestDate(), context.getAggregateByDateUnit())
                .plus(1, mapToChronoUnit(unit));
    final Duration automaticIntervalDuration =
        getDateHistogramIntervalDurationFromMinMax(context.getMinMaxStats());
    final Map<String, Query> filters = new HashMap<>();
    for (ZonedDateTime currentBucketStart = startOfFirstBucket;
        currentBucketStart.isBefore(endOfLastBucket);
        currentBucketStart = getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration)) {
      // to use our correct date formatting we need to switch back to OffsetDateTime
      final String startAsString = dateTimeFormatter.format(currentBucketStart.toOffsetDateTime());
      final String endAsString =
          dateTimeFormatter.format(
              getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration)
                  .toOffsetDateTime());
      final Query query =
          and(
              lt(context.getDateField(), endAsString),
              or(
                  gte(context.getRunningDateReportEndDateField(), startAsString),
                  not(exists(context.getRunningDateReportEndDateField()))));

      filters.put(startAsString, query);
    }

    return Pair.of(
        FILTER_LIMITED_AGGREGATION,
        withSubaggregations(filtersAggregation(filters), context.getSubAggregations()));
  }

  private Pair<String, Aggregation> createAutomaticIntervalAggregationOrFallbackToMonth(
      final DateAggregationContextOS context,
      final Function<DateAggregationContextOS, Pair<String, Aggregation>>
          defaultAggregationCreator) {
    return createAutomaticIntervalAggregationWithSubAggregation(context)
        .map(
            automaticIntervalAggregation ->
                wrapWithFilterLimitedParentAggregation(matchAll(), automaticIntervalAggregation))
        // automatic interval not possible, return default aggregation with unit month instead
        .orElse(defaultAggregation(context, defaultAggregationCreator));
  }

  private Pair<String, Aggregation> defaultAggregation(
      final DateAggregationContextOS context,
      final Function<DateAggregationContextOS, Pair<String, Aggregation>>
          defaultAggregationCreator) {
    context.setAggregateByDateUnit(AggregateByDateUnit.MONTH);
    return defaultAggregationCreator.apply(context);
  }

  private Optional<Pair<String, Aggregation>> createAutomaticIntervalAggregationWithSubAggregation(
      final DateAggregationContextOS context) {
    if (!context.getMinMaxStats().isValidRange()) {
      return Optional.empty();
    }

    final ZonedDateTime min = context.getEarliestDate();
    final ZonedDateTime max = context.getLatestDate();

    final Duration intervalDuration =
        getDateHistogramIntervalDurationFromMinMax(context.getMinMaxStats());
    ZonedDateTime start = min;

    final List<DateRangeExpression> ranges = new ArrayList<>();
    do {
      // this is a do while loop to ensure there's always at least one bucket, even when min and max
      // are equal
      final ZonedDateTime nextStart = start.plus(intervalDuration);
      final boolean isLast = nextStart.isAfter(max) || nextStart.isEqual(max);
      // plus 1 ms because the end of the range is exclusive yet we want to make sure max falls into
      // the last bucket
      final ZonedDateTime end = isLast ? nextStart.plus(1, ChronoUnit.MILLIS) : nextStart;

      final DateRangeExpression range =
          new DateRangeExpression.Builder()
              .key(dateTimeFormatter.format(start))
              .from(fieldDateMath(dateTimeFormatter.format(start)))
              .to(fieldDateMath(dateTimeFormatter.format(end)))
              .build();

      ranges.add(range);
      start = nextStart;
    } while (start.isBefore(max));

    final String aggregationName = context.getDateAggregationName().orElse(DATE_AGGREGATION);
    final Aggregation rangeAgg =
        new Aggregation.Builder()
            .aggregations(context.getSubAggregations())
            .dateRange(
                b ->
                    b.field(context.getDateField())
                        .timeZone(min.getZone().getDisplayName(TextStyle.SHORT, Locale.US))
                        .ranges(ranges))
            .build();

    return Optional.of(Pair.of(aggregationName, rangeAgg));
  }

  private Pair<String, Aggregation> createFilterLimitedDecisionDateHistogramWithSubAggregation(
      final DateAggregationContextOS context) {
    final Pair<String, Aggregation> dateHistogramAggregation =
        createDateHistogramAggregation(
            context,
            builder -> extendBounds(context, dateTimeFormatter).ifPresent(builder::extendedBounds));
    final List<Query> limitFilterQuery = createDecisionDateHistogramLimitingFilter(context);
    return wrapWithFilterLimitedParentAggregation(
        filter(limitFilterQuery), dateHistogramAggregation);
  }

  private Pair<String, Aggregation> createFilterLimitedProcessDateHistogramWithSubAggregation(
      final DateAggregationContextOS context) {
    final Pair<String, Aggregation> dateHistogramAggregation =
        createDateHistogramAggregation(context, extendBoundsConsumer(context));
    final Query limitFilterQuery = createProcessDateHistogramLimitingFilterQuery(context);
    return wrapWithFilterLimitedParentAggregation(limitFilterQuery, dateHistogramAggregation);
  }

  private Consumer<DateHistogramAggregation.Builder> extendBoundsConsumer(
      final DateAggregationContextOS context) {
    return (final DateHistogramAggregation.Builder builder) -> {
      final ProcessQueryFilterEnhancerOS queryFilterEnhancer =
          context.getProcessQueryFilterEnhancer();
      final List<DateFilterDataDto<?>> dateFilters =
          context.isStartDateAggregation()
              ? queryFilterEnhancer.extractInstanceFilters(
                  context.getProcessFilters(), InstanceStartDateFilterDto.class)
              : queryFilterEnhancer.extractInstanceFilters(
                  context.getProcessFilters(), InstanceEndDateFilterDto.class);
      if (!dateFilters.isEmpty()) {
        getExtendedBoundsFromDateFilters(dateFilters, dateTimeFormatter, context)
            .ifPresent(builder::extendedBounds);
      }
    };
  }

  private static Query createProcessDateHistogramLimitingFilterQuery(
      final DateAggregationContextOS context) {
    final ProcessQueryFilterEnhancerOS queryFilterEnhancer =
        context.getProcessQueryFilterEnhancer();
    final List<DateFilterDataDto<?>> startDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceStartDateFilterDto.class);
    final List<DateFilterDataDto<?>> endDateFilters =
        queryFilterEnhancer.extractInstanceFilters(
            context.getProcessFilters(), InstanceEndDateFilterDto.class);
    final List<Query> limitFilterQueries =
        context.isStartDateAggregation()
            ?
            // if custom end filters and no startDateFilters are present, limit based on them
            !endDateFilters.isEmpty() && startDateFilters.isEmpty()
                ? createFilterBoolQueryBuilder(
                    endDateFilters,
                    queryFilterEnhancer.getInstanceEndDateQueryFilter(),
                    context.getFilterContext())
                : createFilterBoolQueryBuilder(
                    startDateFilters,
                    queryFilterEnhancer.getInstanceStartDateQueryFilter(),
                    context.getFilterContext())
            :
            // if custom start filters and no endDateFilters are present, limit based on them
            endDateFilters.isEmpty() && !startDateFilters.isEmpty()
                ? createFilterBoolQueryBuilder(
                    startDateFilters,
                    queryFilterEnhancer.getInstanceStartDateQueryFilter(),
                    context.getFilterContext())
                : createFilterBoolQueryBuilder(
                    endDateFilters,
                    queryFilterEnhancer.getInstanceEndDateQueryFilter(),
                    context.getFilterContext());
    return filter(limitFilterQueries);
  }

  private Pair<String, Aggregation> createFilterLimitedModelElementDateHistogramWithSubAggregation(
      final DateAggregationContextOS context) {
    final Pair<String, Aggregation> dateHistogramAggregation =
        createDateHistogramAggregation(context, x -> {});
    final Query limitFilterQuery =
        createModelElementDateHistogramLimitingFilterQueryFor(context, dateTimeFormatter);
    return wrapWithFilterLimitedParentAggregation(limitFilterQuery, dateHistogramAggregation);
  }
}
