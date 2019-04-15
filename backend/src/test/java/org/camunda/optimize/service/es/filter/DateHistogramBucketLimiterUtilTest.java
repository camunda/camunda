/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class DateHistogramBucketLimiterUtilTest {

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {ChronoUnit.MINUTES, 10L, 0L, 3L},
      {ChronoUnit.HOURS, 5L, 0L, 3L},
      {ChronoUnit.DAYS, 7L, 1L, 2L},
      {ChronoUnit.WEEKS, 100L, 30L, 10L},
      {ChronoUnit.MONTHS, 1000L, 100L, 100L},
      // this one meets the limit already, this verifies the filter doesn't get modified if fine
      {ChronoUnit.YEARS, 1L, 0L, 1L}
    });
  }

  private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private ChronoUnit unit;
  private long startAmountToSubtract;
  private long endAmountToSubtract;
  private Long limit;

  public DateHistogramBucketLimiterUtilTest(ChronoUnit unit,
                                            long startAmountToSubtract,
                                            long endAmountToSubtract,
                                            Long limit) {
    this.unit = unit;
    this.startAmountToSubtract = startAmountToSubtract;
    this.endAmountToSubtract = endAmountToSubtract;
    this.limit = limit;
  }

  @Test
  public void testLimitFixedDateFilterToMaxBucketsForUnit() {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto dateFilterDataDto = createFixedDateFilter(now);

    final FixedDateFilterDataDto limitedFixedDateFilter =
      DateHistogramBucketLimiterUtil.limitFixedDateFilterToMaxBucketsForUnit(unit, dateFilterDataDto, limit.intValue());

    assertThat(limitedFixedDateFilter.getStart(), is(dateFilterDataDto.getEnd().minus(limit, unit)));
    assertThat(limitedFixedDateFilter.getEnd(), is(dateFilterDataDto.getEnd()));
  }

  @Test
  public void testLimitRelativeDateFilterToMaxBucketsForUnit() {
    final OffsetDateTime now = OffsetDateTime.now();

    final RelativeDateFilterDataDto dateFilterDataDto = createRelativeDateFilter(now);

    final RelativeDateFilterDataDto limitedFixedDateFilter =
      DateHistogramBucketLimiterUtil.limitRelativeDateFilterToMaxBucketsForUnit(
        unit,
        dateFilterDataDto,
        limit.intValue()
      );

    assertThat(limitedFixedDateFilter.getStart().getUnit(), is(RelativeDateFilterUnit.valueOf(unit.name())));
    assertThat(limitedFixedDateFilter.getStart().getValue(), is(limit));
    assertThat(limitedFixedDateFilter.getEnd(), is(dateFilterDataDto.getEnd()));
  }

  @Test
  public void testCreateHistogramBucketLimitingFilterFor_defaultFilters_sameUnitForFilterAndGroup() {
    final GroupByDateUnit groupByUnit = GroupByDateUnit.valueOf(unit.name().substring(0, unit.name().length() - 1));
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createHistogramBucketLimitingFilterFor(
        ImmutableList.of(),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate, is(toDate.minus(limit, unit)));
    });
  }

  @Test
  public void testCreateHistogramBucketLimitingFilterFor_defaultFilters_differentUnitForFilterAndGroup() {
    final GroupByDateUnit groupByUnit = GroupByDateUnit.MINUTE;
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createHistogramBucketLimitingFilterFor(
        ImmutableList.of(),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate, is(greaterThanOrEqualTo(toDate.minus(limit, ChronoUnit.MINUTES))));
    });
  }

  @Test
  public void testCreateHistogramBucketLimitingFilterFor_sameUnitForFilterAndGroup() {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto fixedDateFilterDataDto = createFixedDateFilter(now);
    final RelativeDateFilterDataDto relativeDateFilterDataDto = createRelativeDateFilter(now);

    final GroupByDateUnit groupByUnit = GroupByDateUnit.valueOf(unit.name().substring(0, unit.name().length() - 1));
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createHistogramBucketLimitingFilterFor(
        ImmutableList.of(fixedDateFilterDataDto, relativeDateFilterDataDto),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate, is(toDate.minus(limit, unit)));
    });
  }

  @Test
  public void testCreateHistogramBucketLimitingFilterFor_differentUnitForFilterAndGroup() {
    final OffsetDateTime now = OffsetDateTime.now();

    final FixedDateFilterDataDto fixedDateFilterDataDto = createFixedDateFilter(now);
    final RelativeDateFilterDataDto relativeDateFilterDataDto = createRelativeDateFilter(now);

    final GroupByDateUnit groupByUnit = GroupByDateUnit.MINUTE;
    final BoolQueryBuilder filterQuery =
      DateHistogramBucketLimiterUtil.createHistogramBucketLimitingFilterFor(
        ImmutableList.of(fixedDateFilterDataDto, relativeDateFilterDataDto),
        groupByUnit,
        limit.intValue(),
        new StartDateQueryFilter(dateTimeFormatter)
      );

    filterQuery.filter().forEach(queryBuilder -> {
      final RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) queryBuilder;
      final OffsetDateTime fromDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.from()));
      final OffsetDateTime toDate = OffsetDateTime.from(dateTimeFormatter.parse((String) rangeQueryBuilder.to()));

      assertThat(fromDate, is(greaterThanOrEqualTo(toDate.minus(limit, ChronoUnit.MINUTES))));
    });
  }

  private FixedDateFilterDataDto createFixedDateFilter(final OffsetDateTime now) {
    final FixedDateFilterDataDto dateFilterDataDto = new FixedDateFilterDataDto();
    dateFilterDataDto.setStart(now.minus(startAmountToSubtract, unit));
    dateFilterDataDto.setEnd(now.minus(endAmountToSubtract, unit));
    return dateFilterDataDto;
  }

  private RelativeDateFilterDataDto createRelativeDateFilter(final OffsetDateTime now) {
    final RelativeDateFilterDataDto dateFilterDataDto = new RelativeDateFilterDataDto();
    dateFilterDataDto.setStart(new RelativeDateFilterStartDto(
      startAmountToSubtract,
      RelativeDateFilterUnit.valueOf(unit.name())
    ));
    dateFilterDataDto.setEnd(now.minus(endAmountToSubtract, unit));
    return dateFilterDataDto;
  }
}
