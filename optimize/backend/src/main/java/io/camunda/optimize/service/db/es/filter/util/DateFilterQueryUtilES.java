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
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;
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
import org.slf4j.Logger;

public final class DateFilterQueryUtilES {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DateFilterQueryUtilES.class);

  private DateFilterQueryUtilES() {}

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
                (RollingDateFilterStartDto) dateFilterDto.getStart(), dateField, timezone)
            .map(RangeQuery.Builder::build);
      case RELATIVE:
        return createRelativeDateFilter(
                (RelativeDateFilterStartDto) dateFilterDto.getStart(), dateField, timezone)
            .map(RangeQuery.Builder::build);
      default:
        LOG.warn("Cannot execute date filter. Unknown type [{}]", dateFilterDto.getType());
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

    final RangeQuery.Builder builder = new RangeQuery.Builder();
    builder.field(dateField);
    if (end != null) {
      final OffsetDateTime endDateWithCorrectTimezone =
          end.atZoneSameInstant(timezone).toOffsetDateTime();
      builder.lte(JsonData.of(FORMATTER.format(endDateWithCorrectTimezone)));
    }
    if (start != null) {
      final OffsetDateTime startDateWithCorrectTimezone =
          start.atZoneSameInstant(timezone).toOffsetDateTime();
      builder.gte(JsonData.of(FORMATTER.format(startDateWithCorrectTimezone)));
    }
    builder.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(builder.build());
  }

  private static Optional<RangeQuery.Builder> createRollingDateFilter(
      final RollingDateFilterStartDto startDto, final String dateField, final ZoneId timezone) {
    if (startDto == null || startDto.getUnit() == null || startDto.getValue() == null) {
      return Optional.empty();
    }

    final RangeQuery.Builder queryDate = new RangeQuery.Builder();
    queryDate.field(dateField);
    final OffsetDateTime now = LocalDateUtil.getCurrentTimeWithTimezone(timezone);
    queryDate.lte(JsonData.of(FORMATTER.format(now)));

    if (QUARTERS.equals(startDto.getUnit())) {
      LOG.warn(
          "Cannot create date filter: {} is not supported for rolling date filters",
          startDto.getUnit());
      throw new OptimizeValidationException(
          String.format("%s is not supported for rolling date filters", startDto.getUnit()));
    }

    final OffsetDateTime dateBeforeGivenFilter =
        now.minus(
            startDto.getValue(), ChronoUnit.valueOf(startDto.getUnit().getId().toUpperCase()));
    queryDate.gte(JsonData.of(FORMATTER.format(dateBeforeGivenFilter)));
    queryDate.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(queryDate);
  }

  private static Optional<RangeQuery.Builder> createRelativeDateFilter(
      final RelativeDateFilterStartDto startDto, final String dateField, final ZoneId timezone) {
    if (startDto == null || startDto.getUnit() == null || startDto.getValue() == null) {
      return Optional.empty();
    }

    final RangeQuery.Builder queryDate = new RangeQuery.Builder();
    queryDate.field(dateField);
    final OffsetDateTime now = LocalDateUtil.getCurrentTimeWithTimezone(timezone);
    if (startDto.getValue() == 0) {
      queryDate.lte(JsonData.of(FORMATTER.format(now)));
      queryDate.gte(
          JsonData.of(
              FORMATTER.format(DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit()))));
    } else {
      final OffsetDateTime startOfCurrentInterval =
          DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit());
      final OffsetDateTime startOfPreviousInterval =
          DateFilterUtil.getStartOfPreviousInterval(
              startOfCurrentInterval, startDto.getUnit(), startDto.getValue());
      queryDate.lt(JsonData.of(FORMATTER.format(startOfCurrentInterval)));
      queryDate.gte(JsonData.of(FORMATTER.format(startOfPreviousInterval)));
    }
    queryDate.format(OPTIMIZE_DATE_FORMAT);
    return Optional.of(queryDate);
  }
}
