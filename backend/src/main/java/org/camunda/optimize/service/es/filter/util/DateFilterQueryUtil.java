/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.DateFilterUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit.QUARTERS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateFilterQueryUtil {
  private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  public static void addFilters(final BoolQueryBuilder query,
                                final List<DateFilterDataDto<?>> dates,
                                final String dateField,
                                final ZoneId timezone) {
    if (dates != null) {
      for (DateFilterDataDto<?> dateDto : dates) {
        createRangeQuery(dateDto, dateField, timezone).ifPresent(query::filter);
      }
    }
  }

  public static Optional<RangeQueryBuilder> createRangeQuery(final DateFilterDataDto<?> dateFilterDto,
                                                             final String dateField,
                                                             final ZoneId timezone) {
    switch (dateFilterDto.getType()) {
      case FIXED:
        return createFixedDateFilter(
          (OffsetDateTime) dateFilterDto.getStart(),
          dateFilterDto.getEnd(),
          dateField,
          timezone
        );
      case ROLLING:
        return createRollingDateFilter((RollingDateFilterStartDto) dateFilterDto.getStart(), dateField, timezone);
      case RELATIVE:
        return createRelativeDateFilter((RelativeDateFilterStartDto) dateFilterDto.getStart(), dateField, timezone);
      default:
        log.warn("Cannot execute date filter. Unknown type [{}]", dateFilterDto.getType());
        return Optional.empty();
    }
  }

  private static Optional<RangeQueryBuilder> createFixedDateFilter(final OffsetDateTime start,
                                                                   final OffsetDateTime end,
                                                                   final String dateField,
                                                                   final ZoneId timezone) {
    if (end == null && start == null) {
      return Optional.empty();
    }

    final RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(dateField);
    if (end != null) {
      final OffsetDateTime endDateWithCorrectTimezone =
        end.atZoneSameInstant(timezone).toOffsetDateTime();
      queryDate.lte(formatter.format(endDateWithCorrectTimezone));
    }
    if (start != null) {
      final OffsetDateTime startDateWithCorrectTimezone =
        start.atZoneSameInstant(timezone).toOffsetDateTime();
      queryDate.gte(formatter.format(startDateWithCorrectTimezone));
    }
    queryDate.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(queryDate);
  }

  private static Optional<RangeQueryBuilder> createRollingDateFilter(final RollingDateFilterStartDto startDto,
                                                                     final String dateField,
                                                                     final ZoneId timezone) {
    if (startDto == null || startDto.getUnit() == null || startDto.getValue() == null) {
      return Optional.empty();
    }

    final RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(dateField);
    final OffsetDateTime now = LocalDateUtil.getCurrentTimeWithTimezone(timezone);
    queryDate.lte(formatter.format(now));

    if (QUARTERS.equals(startDto.getUnit())) {
      log.warn("Cannot create date filter: {} is not supported for rolling date filters", startDto.getUnit());
      throw new OptimizeValidationException(
        String.format("%s is not supported for rolling date filters", startDto.getUnit())
      );
    }

    final OffsetDateTime dateBeforeGivenFilter = now.minus(
      startDto.getValue(), ChronoUnit.valueOf(startDto.getUnit().getId().toUpperCase())
    );
    queryDate.gte(formatter.format(dateBeforeGivenFilter));
    queryDate.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(queryDate);
  }

  private static Optional<RangeQueryBuilder> createRelativeDateFilter(final RelativeDateFilterStartDto startDto,
                                                                      final String dateField,
                                                                      final ZoneId timezone) {
    if (startDto == null || startDto.getUnit() == null || startDto.getValue() == null) {
      return Optional.empty();
    }

    final RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(dateField);
    final OffsetDateTime now = LocalDateUtil.getCurrentTimeWithTimezone(timezone);
    if (startDto.getValue() == 0) {
      queryDate.lte(formatter.format(now));
      queryDate.gte(formatter.format(DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit())));
    } else {
      final OffsetDateTime startOfCurrentInterval = DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit());
      final OffsetDateTime startOfPreviousInterval = DateFilterUtil.getStartOfPreviousInterval(
        startOfCurrentInterval,
        startDto.getUnit(),
        startDto.getValue()
      );
      queryDate.lt(formatter.format(startOfCurrentInterval));
      queryDate.gte(formatter.format(startOfPreviousInterval));
    }
    queryDate.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(queryDate);
  }

}
