/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.service;

import static io.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.es.filter.util.DateHistogramFilterUtilES.createModelElementDateHistogramLimitingFilterFor;
import static io.camunda.optimize.service.db.es.filter.util.DateHistogramFilterUtilES.extendBoundsAndCreateDecisionDateHistogramLimitingFilterFor;
import static io.camunda.optimize.service.db.es.filter.util.DateHistogramFilterUtilES.extendBoundsAndCreateProcessDateHistogramLimitingFilterFor;
import static io.camunda.optimize.service.db.es.report.interpreter.util.AggregateByDateUnitMapperES.mapToCalendarInterval;
import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.wrapWithFilterLimitedParentAggregation;
import static io.camunda.optimize.service.db.report.interpreter.util.AggregateByDateUnitMapper.mapToChronoUnit;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregation.Builder;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.DateRangeAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FieldDateMath;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketAggregateBase;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketBase;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.util.NamedValue;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.service.DateAggregationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Conditional(ElasticSearchCondition.class)
public class DateAggregationServiceES extends DateAggregationService {

  private static final String DATE_AGGREGATION = "dateAggregation";

  private final DateTimeFormatter dateTimeFormatter;

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
      createProcessInstanceDateAggregation(final DateAggregationContextES context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
          context, this::createFilterLimitedProcessDateHistogramWithSubAggregation);
    }
    return Optional.of(createFilterLimitedProcessDateHistogramWithSubAggregation(context));
  }

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
      createModelElementDateAggregation(final DateAggregationContextES context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
          context, this::createFilterLimitedModelElementDateHistogramWithSubAggregation);
    }
    return Optional.of(createFilterLimitedModelElementDateHistogramWithSubAggregation(context));
  }

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>> createDateVariableAggregation(
      final DateAggregationContextES context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
          context, this::createDateHistogramWithSubAggregation);
    }

    return Optional.of(createDateHistogramWithSubAggregation(context));
  }

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
      createDecisionEvaluationDateAggregation(final DateAggregationContextES context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
          context, this::createFilterLimitedDecisionDateHistogramWithSubAggregation);
    }

    return Optional.of(createFilterLimitedDecisionDateHistogramWithSubAggregation(context));
  }

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>> createRunningDateAggregation(
      final DateAggregationContextES context) {
    if (!context.getMinMaxStats().isMinValid()) {
      return Optional.empty();
    }

    if (AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())
        && !context.getMinMaxStats().isValidRange()) {
      context.setAggregateByDateUnit(AggregateByDateUnit.MONTH);
    }

    return Optional.of(createRunningDateFilterAggregations(context));
  }

  public static Duration getDateHistogramIntervalDurationFromMinMax(
      final MinMaxStatDto minMaxStats) {
    final long intervalFromMinToMax =
        (long) (minMaxStats.getMax() - minMaxStats.getMin())
            / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    // we need to ensure that the interval is > 1 since we create the range buckets based on this
    // interval and it will cause an endless loop if the interval is 0.
    return Duration.of(Math.max(intervalFromMinToMax, 1), ChronoUnit.MILLIS);
  }

  public Map<String, Map<String, Aggregate>> mapDateAggregationsToKeyAggregationMap(
      final Map<String, Aggregate> aggregations, final ZoneId timezone) {
    return mapDateAggregationsToKeyAggregationMap(
        (MultiBucketAggregateBase<? extends MultiBucketBase>)
            aggregations.get(DATE_AGGREGATION)._get(),
        timezone);
  }

  public Map<String, Map<String, Aggregate>> mapDateAggregationsToKeyAggregationMap(
      final MultiBucketAggregateBase<? extends MultiBucketBase> aggr, final ZoneId timezone) {

    Map<String, Map<String, Aggregate>> formattedKeyToBucketMap = new LinkedHashMap<>();
    for (final MultiBucketBase entry : aggr.buckets().array()) {
      final String formattedDate =
          formatToCorrectTimezone(
              entry instanceof DateHistogramBucket
                  ? ((DateHistogramBucket) entry).keyAsString()
                  : ((RangeBucket) entry).key(),
              timezone,
              dateTimeFormatter);
      formattedKeyToBucketMap.put(formattedDate, entry.aggregations());
    }
    // sort in descending order
    formattedKeyToBucketMap =
        formattedKeyToBucketMap.entrySet().stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (u, v) -> {
                      throw new IllegalStateException(String.format("Duplicate key %s", u));
                    },
                    LinkedHashMap::new));
    return formattedKeyToBucketMap;
  }

  private Pair<String, Builder> createDateHistogramAggregation(
      final DateAggregationContextES context) {

    final DateHistogramAggregation.Builder builder = new DateHistogramAggregation.Builder();
    builder
        .field(context.getDateField())
        .format(OPTIMIZE_DATE_FORMAT)
        .calendarInterval(mapToCalendarInterval(context.getAggregateByDateUnit()))
        .timeZone(context.getTimezone().toString())
        .order(NamedValue.of("_key", SortOrder.Desc));

    if (context.isExtendBoundsToMinMaxStats()
        && context.getMinMaxStats().isMaxValid()
        && context.getMinMaxStats().isMinValid()) {

      builder.extendedBounds(
          e ->
              e.min(FieldDateMath.of(f -> f.value(context.getMinMaxStats().getMin())))
                  .max(FieldDateMath.of(f -> f.value(context.getMinMaxStats().getMax()))));
    }

    return Pair.of(context.getDateAggregationName().orElse(DATE_AGGREGATION), builder);
  }

  private Map<String, Aggregation.Builder.ContainerBuilder> createDateHistogramWithSubAggregation(
      final DateAggregationContextES context) {
    final DateHistogramAggregation.Builder builder = new DateHistogramAggregation.Builder();
    builder
        .field(context.getDateField())
        .format(OPTIMIZE_DATE_FORMAT)
        .calendarInterval(mapToCalendarInterval(context.getAggregateByDateUnit()))
        .timeZone(context.getTimezone().toString())
        .order(NamedValue.of("_key", SortOrder.Desc));

    final Aggregation.Builder.ContainerBuilder abuilder =
        new Aggregation.Builder().dateHistogram(builder.build());
    context.getSubAggregations().forEach((k, v) -> abuilder.aggregations(k, v.build()));
    return Map.of(context.getDateAggregationName().orElse(DATE_AGGREGATION), abuilder);
  }

  private Map<String, Aggregation.Builder.ContainerBuilder> createRunningDateFilterAggregations(
      final DateAggregationContextES context) {
    final AggregateByDateUnit unit = context.getAggregateByDateUnit();
    final ZonedDateTime startOfFirstBucket = truncateToUnit(context.getEarliestDate(), unit);
    final ZonedDateTime endOfLastBucket =
        AggregateByDateUnit.AUTOMATIC.equals(context.getAggregateByDateUnit())
            ? context.getLatestDate()
            : truncateToUnit(context.getLatestDate(), context.getAggregateByDateUnit())
                .plus(1, mapToChronoUnit(unit));
    final Duration automaticIntervalDuration =
        getDateHistogramIntervalDurationFromMinMax(context.getMinMaxStats());

    final FiltersAggregation.Builder fbuilder = new FiltersAggregation.Builder();

    final Map<String, Query> keyedQueryMap = new HashMap<>();

    for (ZonedDateTime currentBucketStart = startOfFirstBucket;
        currentBucketStart.isBefore(endOfLastBucket);
        currentBucketStart = getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration)) {
      // to use our correct date formatting we need to switch back to OffsetDateTime
      final String startAsString = dateTimeFormatter.format(currentBucketStart.toOffsetDateTime());
      final String endAsString =
          dateTimeFormatter.format(
              getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration)
                  .toOffsetDateTime());

      keyedQueryMap.put(
          startAsString,
          Query.of(
              q ->
                  q.bool(
                      b ->
                          b.must(
                                  m ->
                                      m.range(
                                          r ->
                                              r.date(
                                                  d ->
                                                      d.field(context.getDateField())
                                                          .lt(endAsString))))
                              .must(
                                  m ->
                                      m.bool(
                                          bb ->
                                              bb.should(
                                                      s ->
                                                          s.range(
                                                              r ->
                                                                  r.date(
                                                                      d ->
                                                                          d.field(
                                                                                  context
                                                                                      .getRunningDateReportEndDateField())
                                                                              .gte(startAsString))))
                                                  .should(
                                                      s ->
                                                          s.bool(
                                                              bbb ->
                                                                  bbb.mustNot(
                                                                      mm ->
                                                                          mm.exists(
                                                                              e ->
                                                                                  e.field(
                                                                                      context
                                                                                          .getRunningDateReportEndDateField()))))))))));
    }
    fbuilder.filters(f -> f.keyed(keyedQueryMap));
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().filters(fbuilder.build());
    context.getSubAggregations().forEach((k, v) -> builder.aggregations(k, v.build()));
    return Map.of(FILTER_LIMITED_AGGREGATION, builder);
  }

  private Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
      createAutomaticIntervalAggregationOrFallbackToMonth(
          final DateAggregationContextES context,
          final Function<
                  DateAggregationContextES, Map<String, Aggregation.Builder.ContainerBuilder>>
              defaultAggregationCreator) {
    final Optional<Pair<String, Aggregation.Builder.ContainerBuilder>>
        automaticIntervalAggregation =
            createAutomaticIntervalAggregationWithSubAggregation(context);

    if (automaticIntervalAggregation.isPresent()) {
      final Pair<String, Aggregation.Builder.ContainerBuilder> automaticIntervalAggregationValue =
          automaticIntervalAggregation.get();
      context
          .getSubAggregations()
          .forEach((k, v) -> automaticIntervalAggregationValue.value().aggregations(k, v.build()));
      return Optional.of(
          wrapWithFilterLimitedParentAggregation(
              Query.of(q -> q.bool(b -> b.filter(f -> f.matchAll(m -> m)))),
              Map.of(
                  automaticIntervalAggregationValue.key(),
                  automaticIntervalAggregationValue.value())));
    }

    // automatic interval not possible, return default aggregation with unit month instead
    context.setAggregateByDateUnit(AggregateByDateUnit.MONTH);
    return Optional.of(defaultAggregationCreator.apply(context));
  }

  private Optional<Pair<String, Aggregation.Builder.ContainerBuilder>>
      createAutomaticIntervalAggregationWithSubAggregation(final DateAggregationContextES context) {
    if (!context.getMinMaxStats().isValidRange()) {
      return Optional.empty();
    }

    final ZonedDateTime min = context.getEarliestDate();
    final ZonedDateTime max = context.getLatestDate();

    final Duration intervalDuration =
        getDateHistogramIntervalDurationFromMinMax(context.getMinMaxStats());

    final DateRangeAggregation.Builder rangeAgg = new DateRangeAggregation.Builder();
    rangeAgg.timeZone(min.getZone().toString()).field(context.getDateField());

    ZonedDateTime start = min;

    do {
      // this is a do while loop to ensure there's always at least one bucket, even when min and max
      // are equal
      final ZonedDateTime nextStart = start.plus(intervalDuration);
      final boolean isLast = nextStart.isAfter(max) || nextStart.isEqual(max);
      // plus 1 ms because the end of the range is exclusive yet we want to make sure max falls into
      // the last bucket
      final ZonedDateTime end = isLast ? nextStart.plus(1, ChronoUnit.MILLIS) : nextStart;

      final ZonedDateTime finalStart = start;
      rangeAgg.ranges(
          r ->
              r.from(f -> f.expr(dateTimeFormatter.format(finalStart)))
                  .key(dateTimeFormatter.format(finalStart))
                  .to(f -> f.expr(dateTimeFormatter.format(end))));
      start = nextStart;
    } while (start.isBefore(max));
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().dateRange(rangeAgg.build());
    return Optional.of(Pair.of(context.getDateAggregationName().orElse(DATE_AGGREGATION), builder));
  }

  private Map<String, Aggregation.Builder.ContainerBuilder>
      createFilterLimitedDecisionDateHistogramWithSubAggregation(
          final DateAggregationContextES context) {
    final Pair<String, DateHistogramAggregation.Builder> dateHistogramAggregation =
        createDateHistogramAggregation(context);

    final BoolQuery.Builder limitFilterQuery =
        extendBoundsAndCreateDecisionDateHistogramLimitingFilterFor(
            dateHistogramAggregation.value(), context, dateTimeFormatter);

    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().dateHistogram(dateHistogramAggregation.value().build());
    context.getSubAggregations().forEach((k, v) -> builder.aggregations(k, v.build()));
    return wrapWithFilterLimitedParentAggregation(
        Query.of(q -> q.bool(limitFilterQuery.build())),
        Map.of(dateHistogramAggregation.key(), builder));
  }

  private Map<String, Aggregation.Builder.ContainerBuilder>
      createFilterLimitedProcessDateHistogramWithSubAggregation(
          final DateAggregationContextES context) {
    final Pair<String, DateHistogramAggregation.Builder> dateHistogramAggregation =
        createDateHistogramAggregation(context);

    final BoolQuery.Builder limitFilterQuery =
        extendBoundsAndCreateProcessDateHistogramLimitingFilterFor(
            dateHistogramAggregation.value(), context, dateTimeFormatter);

    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().dateHistogram(dateHistogramAggregation.value().build());
    context.getSubAggregations().forEach((k, v) -> builder.aggregations(k, v.build()));
    return wrapWithFilterLimitedParentAggregation(
        Query.of(q -> q.bool(limitFilterQuery.build())),
        Map.of(dateHistogramAggregation.key(), builder));
  }

  private Map<String, Aggregation.Builder.ContainerBuilder>
      createFilterLimitedModelElementDateHistogramWithSubAggregation(
          final DateAggregationContextES context) {
    final Pair<String, DateHistogramAggregation.Builder> dateHistogramAggregation =
        createDateHistogramAggregation(context);

    final BoolQuery.Builder limitFilterQueryBuilder =
        createModelElementDateHistogramLimitingFilterFor(context, dateTimeFormatter);

    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().dateHistogram(dateHistogramAggregation.value().build());
    context.getSubAggregations().forEach((k, v) -> builder.aggregations(k, v.build()));
    return wrapWithFilterLimitedParentAggregation(
        Query.of(q -> q.bool(limitFilterQueryBuilder.build())),
        Map.of(dateHistogramAggregation.key(), builder));
  }
}
