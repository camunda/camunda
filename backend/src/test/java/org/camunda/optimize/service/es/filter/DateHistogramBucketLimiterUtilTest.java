/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.previousOrSame;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class DateHistogramBucketLimiterUtilTest {

  private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testLimitFixedDateFilterToMaxBucketsForUnit(ChronoUnit unit, long startAmountToSubtract,
                                                          long endAmountToSubtract, Long limit) {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto dateFilterDataDto = createFixedDateFilter(now, unit, startAmountToSubtract, endAmountToSubtract);

    final FixedDateFilterDataDto limitedFixedDateFilter =
      DateHistogramBucketLimiterUtil.limitFixedDateFilterToMaxBucketsForUnit(unit, dateFilterDataDto, limit.intValue());

    assertThat(limitedFixedDateFilter.getStart(), is(calculateExpectedStartDate(dateFilterDataDto.getEnd(), unit, limit)));
    assertThat(limitedFixedDateFilter.getEnd(), is(dateFilterDataDto.getEnd()));
  }

  private OffsetDateTime calculateExpectedStartDate(final OffsetDateTime endDateTime, final ChronoUnit groupByUnit, long limit) {
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
        break;
    }
    return result.minus(limit - 1, groupByUnit);

  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testLimitRelativeDateFilterToMaxBucketsForUnit(ChronoUnit unit, long startAmountToSubtract,
                                                             long endAmountToSubtract, Long limit) {
    final OffsetDateTime now = OffsetDateTime.now();

    final RelativeDateFilterDataDto dateFilterDataDto = createRelativeDateFilter(now, unit, startAmountToSubtract, endAmountToSubtract);

    final RelativeDateFilterDataDto limitRelativeDateFilter =
      DateHistogramBucketLimiterUtil.limitRelativeDateFilterToMaxBucketsForUnit(
        unit,
        dateFilterDataDto,
        limit.intValue()
      );

    assertThat(limitRelativeDateFilter.getStart().getUnit(), is(DateFilterUnit.valueOf(unit.name())));
    assertThat(limitRelativeDateFilter.getStart().getValue(), is(limit - 1));
    assertThat(limitRelativeDateFilter.getEnd(), is(dateFilterDataDto.getEnd()));
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testLimitRollingDateFilterToMaxBucketsForUnit(ChronoUnit unit, long startAmountToSubtract,
                                                             long endAmountToSubtract, Long limit) {
    final OffsetDateTime now = OffsetDateTime.now();

    final RollingDateFilterDataDto dateFilterDataDto = createRollingDateFilter(now, unit, startAmountToSubtract, endAmountToSubtract);

    final RollingDateFilterDataDto limitedRollingDateFilter =
      DateHistogramBucketLimiterUtil.limitRollingDateFilterToMaxBucketsForUnit(
        unit,
        dateFilterDataDto,
        limit.intValue()
      );

    assertThat(limitedRollingDateFilter.getStart().getUnit(), is(DateFilterUnit.valueOf(unit.name())));
    assertThat(limitedRollingDateFilter.getStart().getValue(), is(limit - 1));
    assertThat(limitedRollingDateFilter.getEnd(), is(dateFilterDataDto.getEnd()));
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_defaultFilters_customDefaultEnd(ChronoUnit unit, long startAmountToSubtract,
                                                                                         long endAmountToSubtract, Long limit) {
    final GroupByDateUnit groupByUnit = GroupByDateUnit.valueOf(unit.name().substring(0, unit.name().length() - 1));
    final OffsetDateTime defaultEndTime = OffsetDateTime.now().minusYears(1L);
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitedOrDefaultLimitedFilter(
        ImmutableList.of(),
        groupByUnit,
        limit.intValue(),
        defaultEndTime,
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(toDate, is(defaultEndTime.truncatedTo(ChronoUnit.MILLIS)));
      assertThat(fromDate, is(calculateExpectedStartDate(toDate, unit, limit)));
    });
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_defaultFilters_sameUnitForFilterAndGroup(ChronoUnit unit, long startAmountToSubtract,
                                                                                                  long endAmountToSubtract, Long limit) {
    final GroupByDateUnit groupByUnit = GroupByDateUnit.valueOf(unit.name().substring(0, unit.name().length() - 1));
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitingFilter(
        ImmutableList.of(),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate, is(calculateExpectedStartDate(toDate, unit, limit)));
    });
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_defaultFilters_differentUnitForFilterAndGroup(ChronoUnit unit, long startAmountToSubtract,
                                                                                                       long endAmountToSubtract, Long limit) {
    final GroupByDateUnit groupByUnit = GroupByDateUnit.MINUTE;
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitingFilter(
        ImmutableList.of(),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate, is(greaterThanOrEqualTo(calculateExpectedStartDate(toDate, ChronoUnit.MINUTES, limit))));
    });
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_sameUnitForFilterAndGroup(ChronoUnit unit, long startAmountToSubtract,
                                                                                   long endAmountToSubtract, Long limit) {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto fixedDateFilterDataDto = createFixedDateFilter(now, unit, startAmountToSubtract, endAmountToSubtract);
    final RelativeDateFilterDataDto relativeDateFilterDataDto = createRelativeDateFilter(now, unit, startAmountToSubtract, endAmountToSubtract);

    final GroupByDateUnit groupByUnit = GroupByDateUnit.valueOf(unit.name().substring(0, unit.name().length() - 1));
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitingFilter(
        ImmutableList.of(fixedDateFilterDataDto, relativeDateFilterDataDto),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate, is(greaterThanOrEqualTo(calculateExpectedStartDate(toDate, unit, limit))));
    });
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_differentUnitForFilterAndGroup(ChronoUnit unit, long startAmountToSubtract,
                                                                                        long endAmountToSubtract, Long limit) {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto fixedDateFilterDataDto = createFixedDateFilter(now, unit, startAmountToSubtract, endAmountToSubtract);
    final RelativeDateFilterDataDto relativeDateFilterDataDto = createRelativeDateFilter(now, unit, startAmountToSubtract, endAmountToSubtract);

    final GroupByDateUnit groupByUnit = GroupByDateUnit.MINUTE;
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitingFilter(
        ImmutableList.of(fixedDateFilterDataDto, relativeDateFilterDataDto),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate, is(greaterThanOrEqualTo(calculateExpectedStartDate(toDate, ChronoUnit.MINUTES, limit))));
    });
  }

  private FixedDateFilterDataDto createFixedDateFilter(final OffsetDateTime now,
                                                       ChronoUnit unit,
                                                       long startAmountToSubtract,
                                                       long endAmountToSubtract) {
    final FixedDateFilterDataDto dateFilterDataDto = new FixedDateFilterDataDto();
    dateFilterDataDto.setStart(now.minus(startAmountToSubtract, unit));
    dateFilterDataDto.setEnd(now.minus(endAmountToSubtract, unit));
    return dateFilterDataDto;
  }

  private RelativeDateFilterDataDto createRelativeDateFilter(final OffsetDateTime now,
                                                             ChronoUnit unit,
                                                             long startAmountToSubtract,
                                                             long endAmountToSubtract) {
    final RelativeDateFilterDataDto dateFilterDataDto = new RelativeDateFilterDataDto();
    dateFilterDataDto.setStart(new RelativeDateFilterStartDto(
      startAmountToSubtract,
      DateFilterUnit.valueOf(unit.name())
    ));
    dateFilterDataDto.setEnd(now.minus(endAmountToSubtract - 1, unit));
    return dateFilterDataDto;
  }

  private RollingDateFilterDataDto createRollingDateFilter(final OffsetDateTime now,
                                                             ChronoUnit unit,
                                                             long startAmountToSubtract,
                                                             long endAmountToSubtract) {
    final RollingDateFilterDataDto dateFilterDataDto = new RollingDateFilterDataDto();
    dateFilterDataDto.setStart(new RollingDateFilterStartDto(
      startAmountToSubtract,
      DateFilterUnit.valueOf(unit.name())
    ));
    dateFilterDataDto.setEnd(now.minus(endAmountToSubtract - 1, unit));
    return dateFilterDataDto;
  }

  private static Stream<Arguments> getHistogramData() {
    return Stream.of(
      Arguments.of(ChronoUnit.MINUTES, 10L, 0L, 3L),
      Arguments.of(ChronoUnit.HOURS, 5L, 0L, 3L),
      Arguments.of(ChronoUnit.DAYS, 7L, 1L, 2L),
      Arguments.of(ChronoUnit.WEEKS, 100L, 30L, 10L),
      Arguments.of(ChronoUnit.MONTHS, 1000L, 100L, 100L),
      // this one meets the limit already, this verifies the filter doesn't get modified if fine
      Arguments.of(ChronoUnit.YEARS, 1L, 0L, 1L)
    );
  }
}
