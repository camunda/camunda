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
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public class DateHistogramBucketLimiterUtil {

  private DateHistogramBucketLimiterUtil() {
  }

  public static BoolQueryBuilder createProcessStartDateHistogramBucketLimitingFilterFor(
    final List<ProcessFilterDto> processFilterDtos,
    final GroupByDateUnit unit,
    final int bucketLimit,
    final ProcessQueryFilterEnhancer queryFilterEnhancer) {

    final BoolQueryBuilder limitFilterQuery;

    final List<DateFilterDataDto> startDateFilters = queryFilterEnhancer.extractFilters(
      processFilterDtos, StartDateFilterDto.class
    );
    final List<DateFilterDataDto> endDateFilters = queryFilterEnhancer.extractFilters(
      processFilterDtos, EndDateFilterDto.class
    );

    // if custom end filters are there limit based on them
    if (!endDateFilters.isEmpty()) {
      limitFilterQuery = createHistogramBucketLimitingFilterFor(
        endDateFilters, unit, bucketLimit, queryFilterEnhancer.getEndDateQueryFilter()
      );
    } else { // otherwise go for startDate filters, even if they are empty, default filters are to be created
      limitFilterQuery = createHistogramBucketLimitingFilterFor(
        startDateFilters, unit, bucketLimit, queryFilterEnhancer.getStartDateQueryFilterService()
      );
    }

    return limitFilterQuery;
  }

  public static BoolQueryBuilder createHistogramBucketLimitingFilterFor(final List<DateFilterDataDto> dateFilters,
                                                                        final GroupByDateUnit unit,
                                                                        final int bucketLimit,
                                                                        final DateQueryFilter queryFilterService) {

    final BoolQueryBuilder limitFilterQuery = boolQuery();
    final ChronoUnit groupByChronoUnit = mapToChronoUnit(unit);
    if (!dateFilters.isEmpty()) {
      // user defined filters present, limit all of them to return less than limit buckets
      final List<DateFilterDataDto> limitedFilters = limitFiltersToMaxBucketsForUnit(
        dateFilters, groupByChronoUnit, bucketLimit
      );
      queryFilterService.addFilters(limitFilterQuery, limitedFilters);
    } else {
      // no filters present generate default limiting filters
      final RelativeDateFilterDataDto defaultFilter = new RelativeDateFilterDataDto();
      defaultFilter.setStart(
        new RelativeDateFilterStartDto((long) bucketLimit, RelativeDateFilterUnit.valueOf(groupByChronoUnit.name()))
      );
      queryFilterService.addFilters(limitFilterQuery, Collections.singletonList(defaultFilter));
    }

    return limitFilterQuery;
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

    final long expectedNumberOfBuckets = groupByUnit.between(
      dateFilter.getStart(),
      dateFilter.getEnd()
    );
    if (expectedNumberOfBuckets > bucketLimit) {
      final OffsetDateTime newStartDate = dateFilter.getEnd().minus(bucketLimit, groupByUnit);

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

    final long expectedNumberOfBuckets = groupByUnit.between(
      OffsetDateTime.now().minus(startDto.getValue(), filterUnit),
      endDate
    );

    if (expectedNumberOfBuckets > bucketLimit) {
      final OffsetDateTime newStartDate = endDate.minus(bucketLimit, groupByUnit);

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
