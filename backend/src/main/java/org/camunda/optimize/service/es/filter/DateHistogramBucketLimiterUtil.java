/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.ExtendedBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
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
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class DateHistogramBucketLimiterUtil {

  private static final Logger logger = LoggerFactory.getLogger(DateHistogramBucketLimiterUtil.class);

  private DateHistogramBucketLimiterUtil() {
  }

  public static BoolQueryBuilder createProcessStartDateHistogramBucketLimitingFilterFor(
    final List<ProcessFilterDto> processFilterDtos,
    final GroupByDateUnit unit,
    final int bucketLimit,
    final OffsetDateTime defaultEndTime,
    final ProcessQueryFilterEnhancer queryFilterEnhancer) {

    final BoolQueryBuilder limitFilterQuery;

    final List<DateFilterDataDto> startDateFilters = queryFilterEnhancer.extractFilters(
      processFilterDtos, StartDateFilterDto.class
    );
    final List<DateFilterDataDto> endDateFilters = queryFilterEnhancer.extractFilters(
      processFilterDtos, EndDateFilterDto.class
    );

    // if custom end filters and no startDateFilters are present, limit based on them
    if (!endDateFilters.isEmpty() && startDateFilters.isEmpty()) {
      limitFilterQuery = createEndDateBucketLimitingFilter(
        endDateFilters, unit, bucketLimit, queryFilterEnhancer.getEndDateQueryFilter()
      );
    } else { // otherwise go for startDate filters, even if they are empty, default filters are to be created
      limitFilterQuery = createDateHistogramBucketLimitingFilter(
        startDateFilters, unit, bucketLimit, defaultEndTime, queryFilterEnhancer.getStartDateQueryFilterService()
      );
    }

    return limitFilterQuery;
  }

  public static BoolQueryBuilder createEndDateBucketLimitingFilter(final List<DateFilterDataDto> dateFilters,
                                                                   final GroupByDateUnit unit,
                                                                   final int bucketLimit,
                                                                   final EndDateQueryFilter endDateQueryFilter) {

    final BoolQueryBuilder limitFilterQuery = boolQuery();
    final ChronoUnit groupByChronoUnit = mapToChronoUnit(unit);
    final List<DateFilterDataDto> limitedFilters = limitFiltersToMaxBucketsForUnit(
      dateFilters, groupByChronoUnit, bucketLimit
    );
    endDateQueryFilter.addFilters(limitFilterQuery, limitedFilters);
    return limitFilterQuery;
  }

  public static BoolQueryBuilder createDateHistogramBucketLimitingFilter(final List<DateFilterDataDto> dateFilters,
                                                                         final GroupByDateUnit unit,
                                                                         final int bucketLimit,
                                                                         final DateQueryFilter queryFilterService) {
    return createDateHistogramBucketLimitingFilter(dateFilters, unit, bucketLimit, null, queryFilterService);
  }

  public static BoolQueryBuilder createDateHistogramBucketLimitingFilter(final List<DateFilterDataDto> dateFilters,
                                                                         final GroupByDateUnit unit,
                                                                         final int bucketLimit,
                                                                         final OffsetDateTime defaultEndTime,
                                                                         final DateQueryFilter queryFilterService) {

    final BoolQueryBuilder limitFilterQuery = boolQuery();

    final List<DateFilterDataDto> limitedFilters = limitDateFiltersToMaxBuckets(
      dateFilters, unit, bucketLimit, true, defaultEndTime
    );

    queryFilterService.addFilters(limitFilterQuery, limitedFilters);
    return limitFilterQuery;
  }

  public static List<DateFilterDataDto> limitDateFiltersToMaxBuckets(final List<DateFilterDataDto> dateFilters,
                                                                     final GroupByDateUnit groupByUnit,
                                                                     final int bucketLimit,
                                                                     final boolean createDefaultFilter,
                                                                     final OffsetDateTime defaultEndTime) {
    final ChronoUnit groupByChronoUnit = mapToChronoUnit(groupByUnit);
    if (!dateFilters.isEmpty()) {
      // user defined filters present, limit all of them to return less than limit buckets
      return limitFiltersToMaxBucketsForUnit(dateFilters, groupByChronoUnit, bucketLimit);
    } else if (createDefaultFilter) {
      // no filters present and createDefaultFilter = true -> generate default limiting filters
      final FixedDateFilterDataDto defaultFilter = new FixedDateFilterDataDto();
      final OffsetDateTime endDateTime = Optional.ofNullable(defaultEndTime).orElse(OffsetDateTime.now());
      defaultFilter.setEnd(endDateTime);
      defaultFilter.setStart(getNewLimitedStartDate(groupByChronoUnit, bucketLimit, endDateTime));
      return Collections.singletonList(defaultFilter);
    }

    return dateFilters;
  }

  public static Optional<ExtendedBounds> getExtendedBoundsFromDateFilters(final List<DateFilterDataDto> dateFilters,
                                                                          final DateTimeFormatter dateFormatter) {

    //in case of several dateFilters, use min (oldest) one as start, and max (newest) one as end
    final Optional<OffsetDateTime> filterStart = getMinDateFilterOffsetDateTime(dateFilters);
    final OffsetDateTime filterEnd = getMaxDateFilterOffsetDateTime(dateFilters);

    return filterStart.map(start -> new ExtendedBounds(dateFormatter.format(start), dateFormatter.format(filterEnd)));
  }

  private static OffsetDateTime getMaxDateFilterOffsetDateTime(final List<DateFilterDataDto> dateFilters) {
    return dateFilters.stream()
      .map(DateFilterDataDto::getEnd)
      .filter(Objects::nonNull)
      .max(OffsetDateTime::compareTo)
      .orElse(OffsetDateTime.now());
  }

  private static Optional<OffsetDateTime> getMinDateFilterOffsetDateTime(final List<DateFilterDataDto> dateFilters) {
    final OffsetDateTime now = OffsetDateTime.now();
    return Stream.concat(
      dateFilters.stream()
        .filter(FixedDateFilterDataDto.class::isInstance)
        .map(date -> (OffsetDateTime) date.getStart()),
      dateFilters.stream()
        .filter(RelativeDateFilterDataDto.class::isInstance)
        .map(filter -> {
          final RelativeDateFilterStartDto startDto = (RelativeDateFilterStartDto) filter.getStart();
          final ChronoUnit filterUnit = ChronoUnit.valueOf(startDto.getUnit().name());
          return now.minus(startDto.getValue(), filterUnit);
        })
    ).min(OffsetDateTime::compareTo);
  }

  public static List<DateFilterDataDto> limitFiltersToMaxBucketsForGroupByUnit(final List<DateFilterDataDto> dateFilters,
                                                                               final GroupByDateUnit groupByUnit,
                                                                               int bucketLimit) {
    final ChronoUnit groupByChronoUnit = mapToChronoUnit(groupByUnit);
    return limitFiltersToMaxBucketsForUnit(dateFilters, groupByChronoUnit, bucketLimit);
  }

  static List<DateFilterDataDto> limitFiltersToMaxBucketsForUnit(final List<DateFilterDataDto> dateFilters,
                                                                 final ChronoUnit groupByUnit,
                                                                 int bucketLimit) {
    return Stream.concat(
      dateFilters.stream()
        .filter(FixedDateFilterDataDto.class::isInstance)
        .map(filterDto -> limitFixedDateFilterToMaxBucketsForUnit(
          groupByUnit, (FixedDateFilterDataDto) filterDto, bucketLimit
        )),
      dateFilters.stream()
        .filter(RelativeDateFilterDataDto.class::isInstance)
        .map(filterDto -> limitRelativeDateFilterToMaxBucketsForUnit(
          groupByUnit, (RelativeDateFilterDataDto) filterDto, bucketLimit
        ))
    ).collect(Collectors.toList());
  }

  static FixedDateFilterDataDto limitFixedDateFilterToMaxBucketsForUnit(final ChronoUnit groupByUnit,
                                                                        final FixedDateFilterDataDto dateFilter,
                                                                        int bucketLimit) {
    long expectedNumberOfBuckets = groupByUnit.between(dateFilter.getStart(), dateFilter.getEnd());
    // +1 as the end of the filter is inclusive
    expectedNumberOfBuckets++;

    if (expectedNumberOfBuckets > bucketLimit) {
      final OffsetDateTime endDateTime = dateFilter.getEnd();
      final OffsetDateTime newStartDate = getNewLimitedStartDate(groupByUnit, bucketLimit, endDateTime);

      final FixedDateFilterDataDto reducedBucketsFilter = new FixedDateFilterDataDto();
      reducedBucketsFilter.setStart(newStartDate);
      reducedBucketsFilter.setEnd(dateFilter.getEnd());
      return reducedBucketsFilter;
    } else {
      return dateFilter;
    }
  }

  static RelativeDateFilterDataDto limitRelativeDateFilterToMaxBucketsForUnit(final ChronoUnit groupByUnit,
                                                                              final RelativeDateFilterDataDto dateFilter,
                                                                              int bucketLimit) {

    final RelativeDateFilterStartDto startDto = dateFilter.getStart();
    final ChronoUnit filterUnit = ChronoUnit.valueOf(startDto.getUnit().name());
    final OffsetDateTime endDate = Optional.ofNullable(dateFilter.getEnd()).orElse(OffsetDateTime.now());

    long expectedNumberOfBuckets = groupByUnit.between(
      OffsetDateTime.now().minus(startDto.getValue(), filterUnit),
      endDate
    );
    // +1 as the end of the filter is inclusive
    expectedNumberOfBuckets++;

    if (expectedNumberOfBuckets > bucketLimit) {
      final OffsetDateTime newStartDate = getNewLimitedStartDate(groupByUnit, bucketLimit, endDate);

      final RelativeDateFilterDataDto reducedBucketsFilter = new RelativeDateFilterDataDto();
      reducedBucketsFilter.setStart(new RelativeDateFilterStartDto(
        filterUnit.between(newStartDate, endDate),
        startDto.getUnit()
      ));
      reducedBucketsFilter.setEnd(dateFilter.getEnd());
      return reducedBucketsFilter;
    } else {
      return dateFilter;
    }
  }

  private static OffsetDateTime getNewLimitedStartDate(final ChronoUnit groupByUnit,
                                                       final int bucketLimit,
                                                       final OffsetDateTime endDateTime) {
    // the start date is truncated to the actual groupBy unit to ensure every bucket is complete
    OffsetDateTime result = endDateTime;
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
    return result.minus(bucketLimit - 1, groupByUnit);
  }

  private static ChronoUnit mapToChronoUnit(final GroupByDateUnit unit) {
    final ChronoUnit groupByChronoUnit;
    switch (unit) {
      case YEAR:
        groupByChronoUnit = ChronoUnit.YEARS;
        break;
      case MONTH:
        groupByChronoUnit = ChronoUnit.MONTHS;
        break;
      case WEEK:
        groupByChronoUnit = ChronoUnit.WEEKS;
        break;
      case DAY:
        groupByChronoUnit = ChronoUnit.DAYS;
        break;
      case HOUR:
        groupByChronoUnit = ChronoUnit.HOURS;
        break;
      case MINUTE:
        groupByChronoUnit = ChronoUnit.MINUTES;
        break;
      default:
      case AUTOMATIC:
        throw new IllegalArgumentException("Unsupported unit " + unit);
    }
    return groupByChronoUnit;
  }
}
