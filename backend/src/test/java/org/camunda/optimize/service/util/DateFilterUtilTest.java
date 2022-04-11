/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
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
  public void testStartOfCurrentInterval(DateUnit unit, String expectedStartOfCurrentInterval) {
    OffsetDateTime expected = OffsetDateTime.parse(expectedStartOfCurrentInterval, ISO_OFFSET_DATE_TIME);
    OffsetDateTime result = DateFilterUtil.getStartOfCurrentInterval(CURRENT_TIME, unit);

    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> dateFilterUnitsAndExpectedStartOfInterval() {
    return Stream.of(
      Arguments.of(DateUnit.MINUTES, "2020-07-25T10:15:00+01:00"),
      Arguments.of(DateUnit.HOURS, "2020-07-25T10:00:00+01:00"),
      Arguments.of(DateUnit.DAYS, "2020-07-25T00:00:00+01:00"),
      Arguments.of(DateUnit.WEEKS, "2020-07-20T00:00:00+01:00"),
      Arguments.of(DateUnit.MONTHS, "2020-07-01T00:00:00+01:00"),
      Arguments.of(DateUnit.QUARTERS, "2020-07-01T00:00:00+01:00"),
      Arguments.of(DateUnit.YEARS, "2020-01-01T00:00:00+01:00")
    );
  }

}
