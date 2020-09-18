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
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.previousOrSame;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToAggregateByDateUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class DateHistogramBucketLimiterUtilTest {

  private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testLimitFixedDateFilterToMaxBucketsForUnit(ChronoUnit unit, long startAmountToSubtract,
                                                          long endAmountToSubtract, Long limit) {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto dateFilterDataDto = createFixedDateFilter(
      now, unit, startAmountToSubtract, endAmountToSubtract
    );

    final FixedDateFilterDataDto limitedFixedDateFilter =
      DateHistogramBucketLimiterUtil.limitFixedDateFilterToMaxBucketsForUnit(
        unit,
        dateFilterDataDto,
        limit.intValue(),
        ZoneId.systemDefault()
      );

    if (startAmountToSubtract - endAmountToSubtract >= limit) {
      assertThat(limitedFixedDateFilter.getStart())
        .isEqualTo(calculateExpectedStartDate(dateFilterDataDto.getEnd(), unit, limit));
    } else {
      assertThat(limitedFixedDateFilter.getStart()).isEqualTo(dateFilterDataDto.getStart());
    }
    assertThat(limitedFixedDateFilter.getEnd()).isEqualTo(dateFilterDataDto.getEnd());
  }

  private OffsetDateTime calculateExpectedStartDate(final OffsetDateTime endDateTime,
                                                    final ChronoUnit groupByUnit,
                                                    long limit) {
    // for date calculation we need to respect the timezone to include daylight
    // saving times in the calculation
    ZonedDateTime result = endDateTime.atZoneSameInstant(ZoneId.systemDefault());
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
    return result.minus(limit - 1, groupByUnit).toOffsetDateTime();

  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testLimitRollingDateFilterToMaxBucketsForUnit(ChronoUnit unit, long startAmountToSubtract,
                                                            long endAmountToSubtract, Long limit) {
    final RollingDateFilterDataDto dateFilterDataDto = createRollingDateFilter(unit, startAmountToSubtract);

    final RollingDateFilterDataDto limitRollingDateFilter = DateHistogramBucketLimiterUtil
      .limitRollingDateFilterToMaxBucketsForUnit(unit, dateFilterDataDto, limit.intValue());

    assertThat(limitRollingDateFilter.getStart())
      .extracting(RollingDateFilterStartDto::getUnit, RollingDateFilterStartDto::getValue)
      .containsExactly(DateFilterUnit.valueOf(unit.name()), limit - 1);
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testLimitRelativeDateFilterToMaxBucketsForUnit(ChronoUnit unit, long startAmountToSubtract,
                                                             long endAmountToSubtract, Long limit) {
    final RelativeDateFilterDataDto dateFilterDataDto = createRelativeDateFilter(unit, startAmountToSubtract);

    final RelativeDateFilterDataDto limitedRollingDateFilter = DateHistogramBucketLimiterUtil
      .limitRelativeDateFilterToMaxBucketsForUnit(unit, dateFilterDataDto, limit.intValue(), ZoneId.systemDefault());

    assertThat(limitedRollingDateFilter.getStart())
      .extracting(RelativeDateFilterStartDto::getUnit, RelativeDateFilterStartDto::getValue)
      .containsExactly(DateFilterUnit.valueOf(unit.name()), limit - 1);
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_defaultFilters_customDefaultEnd(ChronoUnit unit,
                                                                                         long startAmountToSubtract,
                                                                                         long endAmountToSubtract,
                                                                                         Long limit) {
    final AggregateByDateUnit groupByUnit = mapToAggregateByDateUnit(unit);
    final ZonedDateTime defaultEndTime = ZonedDateTime.now().minusYears(1L);
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitedOrDefaultLimitedFilter(
        ImmutableList.of(),
        groupByUnit,
        limit.intValue(),
        defaultEndTime,
        new StartDateQueryFilter(new DateFilterQueryService(dateTimeFormatter)),
        ZoneId.systemDefault()
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final ZonedDateTime fromDate = ZonedDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final ZonedDateTime toDate = ZonedDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(toDate).isEqualTo(defaultEndTime.truncatedTo(ChronoUnit.MILLIS));
      assertThat(fromDate)
        .isEqualTo(calculateExpectedStartDate(
          toDate.toOffsetDateTime(),
          unit,
          limit
        ).atZoneSameInstant(ZoneId.systemDefault()));
    });
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_defaultFilters_sameUnitForFilterAndGroup(
    ChronoUnit unit, long startAmountToSubtract, long endAmountToSubtract, Long limit) {
    final AggregateByDateUnit groupByUnit = mapToAggregateByDateUnit(unit);
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitingFilter(
        ImmutableList.of(),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(new DateFilterQueryService(dateTimeFormatter)),
        ZoneId.systemDefault()
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate).isEqualTo(calculateExpectedStartDate(toDate, unit, limit));
    });
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_defaultFilters_differentUnitForFilterAndGroup(
    ChronoUnit unit, long startAmountToSubtract, long endAmountToSubtract, Long limit) {
    final AggregateByDateUnit groupByUnit = AggregateByDateUnit.MINUTE;
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitingFilter(
        ImmutableList.of(),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(new DateFilterQueryService(dateTimeFormatter)),
        ZoneId.systemDefault()
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate).isAfterOrEqualTo(calculateExpectedStartDate(toDate, ChronoUnit.MINUTES, limit));
    });
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_sameUnitForFilterAndGroup(ChronoUnit unit,
                                                                                   long startAmountToSubtract,
                                                                                   long endAmountToSubtract,
                                                                                   Long limit) {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto fixedDateFilterDataDto = createFixedDateFilter(
      now, unit, startAmountToSubtract, endAmountToSubtract
    );
    final RollingDateFilterDataDto rollingDateFilterDataDto = createRollingDateFilter(unit, startAmountToSubtract);

    final AggregateByDateUnit groupByUnit = mapToAggregateByDateUnit(unit);
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitingFilter(
        ImmutableList.of(fixedDateFilterDataDto, rollingDateFilterDataDto),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(new DateFilterQueryService(dateTimeFormatter)),
        ZoneId.systemDefault()
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate).isAfterOrEqualTo(calculateExpectedStartDate(toDate, unit, limit));
    });
  }

  @ParameterizedTest
  @MethodSource("getHistogramData")
  public void testCreateHistogramBucketLimitingFilterFor_differentUnitForFilterAndGroup(ChronoUnit unit,
                                                                                        long startAmountToSubtract,
                                                                                        long endAmountToSubtract,
                                                                                        Long limit) {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto fixedDateFilterDataDto = createFixedDateFilter(
      now, unit, startAmountToSubtract, endAmountToSubtract
    );
    final RollingDateFilterDataDto rollingDateFilterDataDto = createRollingDateFilter(unit, startAmountToSubtract);

    final AggregateByDateUnit groupByUnit = AggregateByDateUnit.MINUTE;
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitingFilter(
        ImmutableList.of(fixedDateFilterDataDto, rollingDateFilterDataDto),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(new DateFilterQueryService(dateTimeFormatter)),
        ZoneId.systemDefault()
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate).isAfterOrEqualTo(calculateExpectedStartDate(toDate, ChronoUnit.MINUTES, limit));
    });
  }

  private FixedDateFilterDataDto createFixedDateFilter(final OffsetDateTime now,
                                                       final ChronoUnit unit,
                                                       final long startAmountToSubtract,
                                                       final long endAmountToSubtract) {
    ZonedDateTime nowWithTimezone = now.atZoneSameInstant(ZoneId.systemDefault());
    return new FixedDateFilterDataDto(
      nowWithTimezone.minus(startAmountToSubtract, unit).toOffsetDateTime(),
      nowWithTimezone.minus(endAmountToSubtract, unit).toOffsetDateTime()
    );
  }

  private RollingDateFilterDataDto createRollingDateFilter(final ChronoUnit unit,
                                                           final long startAmountToSubtract) {
    return new RollingDateFilterDataDto(
      new RollingDateFilterStartDto(startAmountToSubtract, DateFilterUnit.valueOf(unit.name()))
    );
  }

  private RelativeDateFilterDataDto createRelativeDateFilter(final ChronoUnit unit, final long startAmountToSubtract) {
    return new RelativeDateFilterDataDto(
      new RelativeDateFilterStartDto(startAmountToSubtract, DateFilterUnit.valueOf(unit.name()))
    );
  }

  private static Stream<Arguments> getHistogramData() {
    return Stream.of(
      Arguments.of(ChronoUnit.MINUTES, 10L, 0L, 3L),
      Arguments.of(ChronoUnit.HOURS, 5L, 0L, 3L),
      Arguments.of(ChronoUnit.DAYS, 7L, 1L, 2L),
      Arguments.of(ChronoUnit.WEEKS, 100L, 30L, 10L),
      Arguments.of(ChronoUnit.MONTHS, 1000L, 100L, 100L),
      // this one meets the limit already, this verifies the filter doesn't get modified if fine
      Arguments.of(ChronoUnit.YEARS, 1L, 0L, 2L)
    );
  }

}
