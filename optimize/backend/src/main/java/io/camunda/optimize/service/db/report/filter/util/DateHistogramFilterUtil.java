/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.filter.util;

import static io.camunda.optimize.service.util.DateFilterUtil.getStartOfCurrentInterval;
import static io.camunda.optimize.service.util.DateFilterUtil.getStartOfPreviousInterval;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class DateHistogramFilterUtil {
  public static OffsetDateTime getMaxDateFilterOffsetDateTime(
      final List<DateFilterDataDto<?>> dateFilters) {
    return dateFilters.stream()
        .map(DateFilterDataDto::getEnd)
        .filter(Objects::nonNull)
        .max(OffsetDateTime::compareTo)
        .orElse(OffsetDateTime.now());
  }

  public static Optional<OffsetDateTime> getMinDateFilterOffsetDateTime(
      final List<DateFilterDataDto<?>> dateFilters) {
    final OffsetDateTime now = OffsetDateTime.now();
    return Stream.of(
            dateFilters.stream()
                .filter(FixedDateFilterDataDto.class::isInstance)
                .map(date -> (OffsetDateTime) date.getStart())
                .filter(Objects::nonNull), // only consider fixed date filters with a set start
            dateFilters.stream()
                .filter(RollingDateFilterDataDto.class::isInstance)
                .map(
                    filter -> {
                      final RollingDateFilterStartDto startDto =
                          (RollingDateFilterStartDto) filter.getStart();
                      final ChronoUnit filterUnit = ChronoUnit.valueOf(startDto.getUnit().name());
                      return now.minus(startDto.getValue(), filterUnit);
                    }),
            dateFilters.stream()
                .filter(RelativeDateFilterDataDto.class::isInstance)
                .map(
                    filter -> {
                      final RelativeDateFilterStartDto startDto =
                          ((RelativeDateFilterDataDto) filter).getStart();
                      final OffsetDateTime startOfCurrentInterval =
                          getStartOfCurrentInterval(now, startDto.getUnit());
                      if (startDto.getValue() == 0L) {
                        return startOfCurrentInterval;
                      } else {
                        return getStartOfPreviousInterval(
                            startOfCurrentInterval, startDto.getUnit(), startDto.getValue());
                      }
                    }))
        .flatMap(stream -> stream)
        .min(OffsetDateTime::compareTo);
  }
}
