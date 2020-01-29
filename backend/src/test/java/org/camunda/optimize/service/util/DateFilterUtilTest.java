/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;

public class DateFilterUtilTest {

  private static final OffsetDateTime CURRENT_TIME = OffsetDateTime.parse("2020-07-25T10:15:25+01:00", ISO_OFFSET_DATE_TIME);

  @ParameterizedTest
  @MethodSource("dateFilterUnitsAndExpectedStartOfInterval")
  public void testStartOfCurrentInterval(DateFilterUnit unit, String expectedStartOfCurrentInterval) {
    OffsetDateTime expected = OffsetDateTime.parse(expectedStartOfCurrentInterval, ISO_OFFSET_DATE_TIME);
    OffsetDateTime result = DateFilterUtil.getStartOfCurrentInterval(CURRENT_TIME, unit);

    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> dateFilterUnitsAndExpectedStartOfInterval() {
    return Stream.of(
      Arguments.of(DateFilterUnit.MINUTES, "2020-07-25T10:15:00+01:00"),
      Arguments.of(DateFilterUnit.HOURS, "2020-07-25T10:00:00+01:00"),
      Arguments.of(DateFilterUnit.DAYS, "2020-07-25T00:00:00+01:00"),
      Arguments.of(DateFilterUnit.WEEKS, "2020-07-20T00:00:00+01:00"),
      Arguments.of(DateFilterUnit.MONTHS, "2020-07-01T00:00:00+01:00"),
      Arguments.of(DateFilterUnit.QUARTERS, "2020-07-01T00:00:00+01:00"),
      Arguments.of(DateFilterUnit.YEARS, "2020-01-01T00:00:00+01:00")
    );
  }

}
