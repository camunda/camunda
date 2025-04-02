/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter.util;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit.QUARTERS;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.DateRangeQuery.Builder;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.DateFilterUtil;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateFilterQueryUtilES {
  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  public static void addFilters(
      final BoolQuery.Builder query,
      final List<DateFilterDataDto<?>> dates,
      final String dateField,
      final ZoneId timezone) {
    if (dates != null) {
      for (final DateFilterDataDto<?> dateDto : dates) {
        createRangeQuery(dateDto, dateField, timezone)
            .ifPresent(r -> query.filter(f -> f.range(r)));
      }
    }
  }

  public static Optional<RangeQuery> createRangeQuery(
      final DateFilterDataDto<?> dateFilterDto, final String dateField, final ZoneId timezone) {
    switch (dateFilterDto.getType()) {
      case FIXED:
        return createFixedDateFilter(
            (OffsetDateTime) dateFilterDto.getStart(), dateFilterDto.getEnd(), dateField, timezone);
      case ROLLING:
        return createRollingDateFilter(
            (RollingDateFilterStartDto) dateFilterDto.getStart(), dateField, timezone);
      case RELATIVE:
        return createRelativeDateFilter(
            (RelativeDateFilterStartDto) dateFilterDto.getStart(), dateField, timezone);
      default:
        log.warn("Cannot execute date filter. Unknown type [{}]", dateFilterDto.getType());
        return Optional.empty();
    }
  }

  private static Optional<RangeQuery> createFixedDateFilter(
      final OffsetDateTime start,
      final OffsetDateTime end,
      final String dateField,
      final ZoneId timezone) {
    if (end == null && start == null) {
      return Optional.empty();
    }

    final Builder dateBuilder = new Builder();
    dateBuilder.field(dateField);
    if (end != null) {
      final OffsetDateTime endDateWithCorrectTimezone =
          end.atZoneSameInstant(timezone).toOffsetDateTime();
      dateBuilder.lte(formatter.format(endDateWithCorrectTimezone));
    }
    if (start != null) {
      final OffsetDateTime startDateWithCorrectTimezone =
          start.atZoneSameInstant(timezone).toOffsetDateTime();
      dateBuilder.gte(formatter.format(startDateWithCorrectTimezone));
    }
    dateBuilder.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(new RangeQuery.Builder().date(d -> dateBuilder).build());
  }

  private static Optional<RangeQuery> createRollingDateFilter(
      final RollingDateFilterStartDto startDto, final String dateField, final ZoneId timezone) {
    if (startDto == null || startDto.getUnit() == null || startDto.getValue() == null) {
      return Optional.empty();
    }

    final RangeQuery.Builder queryDate = new RangeQuery.Builder();
    final Builder dateBuilder = new Builder();
    dateBuilder.field(dateField);
    final OffsetDateTime now = LocalDateUtil.getCurrentTimeWithTimezone(timezone);
    dateBuilder.lte(formatter.format(now));

    if (QUARTERS.equals(startDto.getUnit())) {
      log.warn(
          "Cannot create date filter: {} is not supported for rolling date filters",
          startDto.getUnit());
      throw new OptimizeValidationException(
          String.format("%s is not supported for rolling date filters", startDto.getUnit()));
    }

    final OffsetDateTime dateBeforeGivenFilter =
        now.minus(
            startDto.getValue(), ChronoUnit.valueOf(startDto.getUnit().getId().toUpperCase()));
    dateBuilder.gte(formatter.format(dateBeforeGivenFilter));
    dateBuilder.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(queryDate.date(dateBuilder.build()).build());
  }

  private static Optional<RangeQuery> createRelativeDateFilter(
      final RelativeDateFilterStartDto startDto, final String dateField, final ZoneId timezone) {
    if (startDto == null || startDto.getUnit() == null || startDto.getValue() == null) {
      return Optional.empty();
    }

    final RangeQuery.Builder queryDate = new RangeQuery.Builder();
    final Builder dateBuilder = new Builder();
    dateBuilder.field(dateField);
    final OffsetDateTime now = LocalDateUtil.getCurrentTimeWithTimezone(timezone);
    if (startDto.getValue() == 0) {
      dateBuilder.lte(formatter.format(now));
      dateBuilder.gte(
          formatter.format(DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit())));
    } else {
      final OffsetDateTime startOfCurrentInterval =
          DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit());
      final OffsetDateTime startOfPreviousInterval =
          DateFilterUtil.getStartOfPreviousInterval(
              startOfCurrentInterval, startDto.getUnit(), startDto.getValue());
      dateBuilder.lt(formatter.format(startOfCurrentInterval));
      dateBuilder.gte(formatter.format(startOfPreviousInterval));
    }
    dateBuilder.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(queryDate.date(dateBuilder.build()).build());
  }
}
