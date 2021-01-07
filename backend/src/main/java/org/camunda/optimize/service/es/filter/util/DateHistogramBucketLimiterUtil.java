/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.QueryFilter;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.LongBounds;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.previousOrSame;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.service.util.DateFilterUtil.getStartOfCurrentInterval;
import static org.camunda.optimize.service.util.DateFilterUtil.getStartOfPreviousInterval;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateHistogramBucketLimiterUtil {

  public static BoolQueryBuilder createModelElementDateHistogramBucketLimitingFilterFor(
    final DateAggregationContext context,
    final DateTimeFormatter dateTimeFormatter,
    final int esBucketLimit) {

    final ZonedDateTime newLimitedStartDate = getNewLimitedStartDate(
      mapToChronoUnit(context.getAggregateByDateUnit()),
      esBucketLimit,
      context.getLatestDate()
    );
    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(context.getDateField())
      .gte(dateTimeFormatter.format(newLimitedStartDate))
      .lte(dateTimeFormatter.format(context.getLatestDate()))
      .format(OPTIMIZE_DATE_FORMAT);
    final BoolQueryBuilder limitFilterQuery = boolQuery();
    limitFilterQuery.filter().add(queryDate);
    return limitFilterQuery;
  }

  public static BoolQueryBuilder extendBoundsAndCreateDecisionDateHistogramBucketLimitingFilterFor(
    final DateHistogramAggregationBuilder dateHistogramAggregation,
    final DateAggregationContext context,
    final DateTimeFormatter dateFormatter,
    final int esBucketLimit) {

    final DecisionQueryFilterEnhancer queryFilterEnhancer = context.getDecisionQueryFilterEnhancer();
    final List<DateFilterDataDto<?>> evaluationDateFilter = queryFilterEnhancer.extractFilters(
      context.getDecisionFilters(), EvaluationDateFilterDto.class);

    final List<DateFilterDataDto<?>> limitedFilters = limitDateFiltersOrCreateDefaultFilter(
      evaluationDateFilter,
      context.getAggregateByDateUnit(),
      esBucketLimit,
      context.getLatestDate(),
      context.getTimezone()
    );

    final BoolQueryBuilder limitFilterQuery = createFilterBoolQueryBuilder(
      limitedFilters,
      queryFilterEnhancer.getEvaluationDateQueryFilter(),
      context.getTimezone()
    );

    if (!evaluationDateFilter.isEmpty()) {
      getExtendedBoundsFromDateFilters(
        limitedFilters,
        dateFormatter
      ).ifPresent(dateHistogramAggregation::extendedBounds);
    }
    return limitFilterQuery;
  }

  public static BoolQueryBuilder extendBoundsAndCreateProcessDateHistogramBucketLimitingFilterFor(
    final DateHistogramAggregationBuilder dateHistogramAggregation,
    final DateAggregationContext context,
    final DateTimeFormatter dateTimeFormatter,
    final int esBucketLimit) {
    if (context.isStartDateAggregation()) {
      return extendBoundsAndCreateProcessStartDateHistogramBucketLimitingFilterFor(
        dateHistogramAggregation,
        context,
        dateTimeFormatter,
        esBucketLimit
      );
    } else {
      return extendBoundsAndCreateProcessEndDateHistogramBucketLimitingFilterFor(
        dateHistogramAggregation,
        context,
        dateTimeFormatter,
        esBucketLimit
      );
    }
  }

  private static BoolQueryBuilder extendBoundsAndCreateProcessStartDateHistogramBucketLimitingFilterFor(
    final DateHistogramAggregationBuilder dateHistogramAggregation,
    final DateAggregationContext context,
    final DateTimeFormatter dateTimeFormatter,
    final int esBucketLimit) {

    final ProcessQueryFilterEnhancer queryFilterEnhancer = context.getProcessQueryFilterEnhancer();

    final List<DateFilterDataDto<?>> startDateFilters = queryFilterEnhancer.extractFilters(
      context.getProcessFilters(), StartDateFilterDto.class
    );
    final List<DateFilterDataDto<?>> endDateFilters = queryFilterEnhancer.extractFilters(
      context.getProcessFilters(), EndDateFilterDto.class
    );

    // if custom end filters and no startDateFilters are present, limit based on them
    final BoolQueryBuilder limitFilterQuery;
    if (!endDateFilters.isEmpty() && startDateFilters.isEmpty()) {
      limitFilterQuery = createDateHistogramBucketLimitingFilter(
        endDateFilters,
        context.getAggregateByDateUnit(),
        esBucketLimit,
        queryFilterEnhancer.getEndDateQueryFilter(),
        context.getTimezone()
      );
    } else {
      // otherwise go for startDate filters, even if they are empty, default filters are to be created and
      // dateHistogram bounds extended
      final List<DateFilterDataDto<?>> limitedFilters = limitDateFiltersOrCreateDefaultFilter(
        startDateFilters,
        context.getAggregateByDateUnit(),
        esBucketLimit,
        context.getLatestDate(),
        context.getTimezone()
      );

      limitFilterQuery = createFilterBoolQueryBuilder(
        limitedFilters,
        queryFilterEnhancer.getStartDateQueryFilter(),
        context.getTimezone()
      );

      if (!startDateFilters.isEmpty()) {
        getExtendedBoundsFromDateFilters(
          limitedFilters,
          dateTimeFormatter
        ).ifPresent(dateHistogramAggregation::extendedBounds);
      }
    }
    return limitFilterQuery;
  }

  private static BoolQueryBuilder extendBoundsAndCreateProcessEndDateHistogramBucketLimitingFilterFor(
    final DateHistogramAggregationBuilder dateHistogramAggregation,
    final DateAggregationContext context,
    final DateTimeFormatter dateTimeFormatter,
    final int esBucketLimit) {

    final ProcessQueryFilterEnhancer queryFilterEnhancer = context.getProcessQueryFilterEnhancer();

    final List<DateFilterDataDto<?>> startDateFilters = queryFilterEnhancer.extractFilters(
      context.getProcessFilters(), StartDateFilterDto.class
    );
    final List<DateFilterDataDto<?>> endDateFilters = queryFilterEnhancer.extractFilters(
      context.getProcessFilters(), EndDateFilterDto.class
    );

    // if custom start filters and no endDateFilters are present, limit based on them
    final BoolQueryBuilder limitFilterQuery;
    if (endDateFilters.isEmpty() && !startDateFilters.isEmpty()) {
      limitFilterQuery = createDateHistogramBucketLimitingFilter(
        startDateFilters,
        context.getAggregateByDateUnit(),
        esBucketLimit,
        queryFilterEnhancer.getStartDateQueryFilter(),
        context.getTimezone()
      );
    } else {
      // otherwise go for endDate filters, even if they are empty, default filters are to be created and
      // dateHistogram bounds extended
      final List<DateFilterDataDto<?>> limitedFilters = limitDateFiltersOrCreateDefaultFilter(
        endDateFilters,
        context.getAggregateByDateUnit(),
        esBucketLimit,
        context.getLatestDate(),
        context.getTimezone()
      );

      limitFilterQuery = createFilterBoolQueryBuilder(
        limitedFilters,
        queryFilterEnhancer.getEndDateQueryFilter(),
        context.getTimezone()
      );

      if (!endDateFilters.isEmpty()) {
        getExtendedBoundsFromDateFilters(
          limitedFilters,
          dateTimeFormatter
        ).ifPresent(dateHistogramAggregation::extendedBounds);
      }
    }

    return limitFilterQuery;
  }

  private static BoolQueryBuilder createFilterBoolQueryBuilder(final List<DateFilterDataDto<?>> filters,
                                                               final QueryFilter<DateFilterDataDto<?>> queryFilter,
                                                               final ZoneId timezone) {
    final BoolQueryBuilder limitFilterQuery = boolQuery();
    queryFilter.addFilters(limitFilterQuery, filters, timezone);
    return limitFilterQuery;
  }

  private static List<DateFilterDataDto<?>> limitDateFiltersOrCreateDefaultFilter(final List<DateFilterDataDto<?>> dateFilters,
                                                                                  final AggregateByDateUnit groupByUnit,
                                                                                  final int bucketLimit,
                                                                                  final ZonedDateTime defaultEndTime,
                                                                                  final ZoneId timezone) {
    final ChronoUnit groupByChronoUnit = mapToChronoUnit(groupByUnit);
    if (dateFilters.isEmpty()) {
      // no filters present -> generate default limiting filters
      return createDefaultFilter(bucketLimit, defaultEndTime, groupByChronoUnit);
    } else {
      // user defined filters present, limit all of them to return less than limit buckets
      return limitFiltersToMaxBucketsForUnit(dateFilters, groupByChronoUnit, bucketLimit, timezone);
    }
  }

  private static List<DateFilterDataDto<?>> createDefaultFilter(final int bucketLimit,
                                                                final ZonedDateTime defaultEndTime,
                                                                final ChronoUnit groupByChronoUnit) {
    final ZonedDateTime endDateTime = Optional.ofNullable(defaultEndTime).orElse(ZonedDateTime.now());
    final FixedDateFilterDataDto defaultFilter = new FixedDateFilterDataDto(
      getNewLimitedStartDate(groupByChronoUnit, bucketLimit, endDateTime).toOffsetDateTime(),
      endDateTime.toOffsetDateTime()
    );
    return Collections.singletonList(defaultFilter);
  }

  private static Optional<LongBounds> getExtendedBoundsFromDateFilters(final List<DateFilterDataDto<?>> dateFilters,
                                                                       final DateTimeFormatter dateFormatter) {

    // in case of several dateFilters, use min (oldest) one as start, and max (newest) one as end
    final Optional<OffsetDateTime> filterStart = getMinDateFilterOffsetDateTime(dateFilters);
    final OffsetDateTime filterEnd = getMaxDateFilterOffsetDateTime(dateFilters);
    return filterStart.map(start -> new LongBounds(dateFormatter.format(start), dateFormatter.format(filterEnd)));
  }

  private static OffsetDateTime getMaxDateFilterOffsetDateTime(final List<DateFilterDataDto<?>> dateFilters) {
    return dateFilters.stream()
      .map(DateFilterDataDto::getEnd)
      .filter(Objects::nonNull)
      .max(OffsetDateTime::compareTo)
      .orElse(OffsetDateTime.now());
  }

  private static Optional<OffsetDateTime> getMinDateFilterOffsetDateTime(final List<DateFilterDataDto<?>> dateFilters) {
    final OffsetDateTime now = OffsetDateTime.now();
    return Stream.of(
      dateFilters.stream()
        .filter(FixedDateFilterDataDto.class::isInstance)
        .map(date -> (OffsetDateTime) date.getStart()),
      dateFilters.stream()
        .filter(RollingDateFilterDataDto.class::isInstance)
        .map(filter -> {
          final RollingDateFilterStartDto startDto = (RollingDateFilterStartDto) filter.getStart();
          final ChronoUnit filterUnit = ChronoUnit.valueOf(startDto.getUnit().name());
          return now.minus(startDto.getValue(), filterUnit);
        }),
      dateFilters.stream()
        .filter(RelativeDateFilterDataDto.class::isInstance)
        .map(filter -> {
          RelativeDateFilterStartDto startDto = ((RelativeDateFilterDataDto) filter).getStart();
          OffsetDateTime startOfCurrentInterval = getStartOfCurrentInterval(now, startDto.getUnit());
          if (startDto.getValue() == 0L) {
            return startOfCurrentInterval;
          } else {
            return getStartOfPreviousInterval(
              startOfCurrentInterval,
              startDto.getUnit(),
              startDto.getValue()
            );
          }
        })
    ).flatMap(stream -> stream).min(OffsetDateTime::compareTo);
  }

  private static List<DateFilterDataDto<?>> limitFiltersToMaxBucketsForUnit(final List<DateFilterDataDto<?>> dateFilters,
                                                                            final ChronoUnit groupByUnit,
                                                                            final int bucketLimit,
                                                                            final ZoneId timezone) {
    return Stream.of(
      dateFilters.stream()
        .filter(FixedDateFilterDataDto.class::isInstance)
        .map(filterDto -> limitFixedDateFilterToMaxBucketsForUnit(
          groupByUnit, (FixedDateFilterDataDto) filterDto, bucketLimit, timezone
        )),
      dateFilters.stream()
        .filter(RollingDateFilterDataDto.class::isInstance)
        .map(filterDto -> limitRollingDateFilterToMaxBucketsForUnit(
          groupByUnit, (RollingDateFilterDataDto) filterDto, bucketLimit
        )),
      dateFilters.stream()
        .filter(RelativeDateFilterDataDto.class::isInstance)
        .map(filterDto -> limitRelativeDateFilterToMaxBucketsForUnit(
          groupByUnit, (RelativeDateFilterDataDto) filterDto, bucketLimit, timezone
        ))
    ).flatMap(stream -> stream).collect(Collectors.toList());
  }

  private static ZonedDateTime getNewLimitedStartDate(final ChronoUnit groupByUnit,
                                                      final int bucketLimit,
                                                      final ZonedDateTime lastDateTime) {
    // for date calculation we need to respect the timezone to include daylight
    // saving times in the calculation
    ZonedDateTime result = lastDateTime;
    // the start date is truncated to the actual groupBy unit to ensure every bucket is complete
    switch (groupByUnit) {
      case YEARS:
        result = result.with(firstDayOfYear()).truncatedTo(ChronoUnit.DAYS);
        break;
      case MONTHS:
        result = result.with(firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS);
        break;
      case WEEKS:
        result = result.with(previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
        break;
      default:
        result = result.truncatedTo(groupByUnit);
    }
    return result.minus(bucketLimit - 1L, groupByUnit);
  }

  public static BoolQueryBuilder createDateHistogramBucketLimitingFilter(final List<DateFilterDataDto<?>> dateFilters,
                                                                         final AggregateByDateUnit unit,
                                                                         final int bucketLimit,
                                                                         final QueryFilter<DateFilterDataDto<?>> queryFilter,
                                                                         final ZoneId timezone) {
    final List<DateFilterDataDto<?>> limitedFilters = limitFiltersToMaxBucketsForUnit(
      dateFilters,
      mapToChronoUnit(unit),
      bucketLimit,
      timezone
    );
    return createFilterBoolQueryBuilder(limitedFilters, queryFilter, timezone);
  }

  public static BoolQueryBuilder createDateHistogramBucketLimitedOrDefaultLimitedFilter(
    final List<DateFilterDataDto<?>> dateFilters,
    final AggregateByDateUnit unit,
    final int bucketLimit,
    final ZonedDateTime defaultEndTime,
    final QueryFilter<DateFilterDataDto<?>> queryFilter,
    final ZoneId timezone) {
    final List<DateFilterDataDto<?>> limitedFilters = limitDateFiltersOrCreateDefaultFilter(
      dateFilters, unit, bucketLimit, defaultEndTime, timezone
    );
    return createFilterBoolQueryBuilder(limitedFilters, queryFilter, timezone);
  }

  public static FixedDateFilterDataDto limitFixedDateFilterToMaxBucketsForUnit(final ChronoUnit groupByUnit,
                                                                               final FixedDateFilterDataDto dateFilter,
                                                                               final int bucketLimit,
                                                                               final ZoneId timezone) {
    long expectedNumberOfBuckets = groupByUnit.between(dateFilter.getStart(), dateFilter.getEnd());
    // +1 as for a value of 0 there is one bucket
    expectedNumberOfBuckets++;

    if (expectedNumberOfBuckets > bucketLimit) {
      final ZonedDateTime endDateTime = dateFilter.getEnd().atZoneSameInstant(timezone);
      final ZonedDateTime newStartDate = getNewLimitedStartDate(groupByUnit, bucketLimit, endDateTime);

      return new FixedDateFilterDataDto(newStartDate.toOffsetDateTime(), dateFilter.getEnd());
    }
    return dateFilter;
  }

  public static RollingDateFilterDataDto limitRollingDateFilterToMaxBucketsForUnit(final ChronoUnit groupByUnit,
                                                                                   final RollingDateFilterDataDto dateFilter,
                                                                                   final int bucketLimit) {
    final RollingDateFilterStartDto startDto = dateFilter.getStart();
    if (startDto.getUnit() == DateFilterUnit.QUARTERS) {
      throw new OptimizeValidationException(String.format(
        "%s are not supported by rolling filters",
        DateFilterUnit.QUARTERS
      ));
    }
    final ChronoUnit filterUnit = ChronoUnit.valueOf(startDto.getUnit().name());
    final ZonedDateTime endDate = ZonedDateTime.now();
    long expectedNumberOfBuckets = groupByUnit.between(endDate.minus(startDto.getValue(), filterUnit), endDate);
    // +1 as for a value of 0 there is one bucket
    expectedNumberOfBuckets++;

    if (expectedNumberOfBuckets > bucketLimit) {
      final ZonedDateTime newStartDate = getNewLimitedStartDate(groupByUnit, bucketLimit, endDate);
      return new RollingDateFilterDataDto(
        new RollingDateFilterStartDto(filterUnit.between(newStartDate, endDate), startDto.getUnit())
      );
    }
    return dateFilter;
  }

  public static RelativeDateFilterDataDto limitRelativeDateFilterToMaxBucketsForUnit(final ChronoUnit groupByUnit,
                                                                                     final RelativeDateFilterDataDto dateFilter,
                                                                                     final int bucketLimit,
                                                                                     final ZoneId timezone) {
    final RelativeDateFilterStartDto startDto = dateFilter.getStart();
    final ZonedDateTime endDate = ZonedDateTime.now();
    ZonedDateTime startOfCurrentInterval =
      getStartOfCurrentInterval(endDate.toOffsetDateTime(), startDto.getUnit()).atZoneSameInstant(timezone);
    long expectedNumberOfBuckets;
    if (startDto.getValue() == 0) {
      expectedNumberOfBuckets = groupByUnit.between(startOfCurrentInterval, endDate);
    } else {
      expectedNumberOfBuckets = groupByUnit.between(
        getStartOfPreviousInterval(startOfCurrentInterval.toOffsetDateTime(), startDto.getUnit(), startDto.getValue()),
        startOfCurrentInterval
      );
    }

    // +1 as for a value of 0 there is one bucket
    expectedNumberOfBuckets++;

    if (expectedNumberOfBuckets > bucketLimit) {
      final ZonedDateTime newStartDate = getNewLimitedStartDate(groupByUnit, bucketLimit, endDate);
      final long newValue;
      if (startDto.getUnit() == DateFilterUnit.QUARTERS) {
        newValue = ChronoUnit.MONTHS.between(newStartDate, endDate) / 3;
      } else {
        newValue = ChronoUnit.valueOf(startDto.getUnit().name()).between(newStartDate, endDate);
      }
      return new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(newValue, startDto.getUnit()));
    }
    return dateFilter;
  }
}
