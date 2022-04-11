/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DateFormatterUtilTest {

  @ParameterizedTest
  @MethodSource("getDateStringsAndValidityExpectations")
  public void checkValidityOfDateFormatStrings(final String dateString, final boolean expectedValidity) {
    // when
    final boolean isValidFormat = DateFormatterUtil.isValidOptimizeDateFormat(dateString);

    // then
    assertThat(isValidFormat).isEqualTo(expectedValidity);
  }

  @ParameterizedTest
  @MethodSource("getDateStringsAndExpectedConversionOutput")
  public void attemptConversionToOptimizeFormattedDates(final String dateStringInput,
                                                        final Optional<String> expectedOutput) {
    // when
    final Optional<String> convertedString = DateFormatterUtil.getDateStringInOptimizeDateFormat(dateStringInput);

    // then
    assertThat(convertedString).isEqualTo(expectedOutput);
  }

  private static Stream<Arguments> getDateStringsAndValidityExpectations() {
    return Stream.of(
      Arguments.of("10/12/2020", false),
      Arguments.of("10-12-2020", false),
      Arguments.of("2020-07-01", false),
      Arguments.of("10/12/2020+03:00", false),
      Arguments.of("2020-07-16T10:14:22.761421", false),
      Arguments.of("2019-06-15T12:00:00.000+02:00", false),
      Arguments.of("2019-06-15T12:00:00.000+0200", true),
      Arguments.of("SOME-NON-PARSEABLE_DATE", false)
    );
  }

  private static Stream<Arguments> getDateStringsAndExpectedConversionOutput() {
    return Stream.of(
      Arguments.of("10/12/2020", Optional.of("2020-12-10T00:00:00.000+0000")),
      Arguments.of("10-12-2020", Optional.of("2020-12-10T00:00:00.000+0000")),
      Arguments.of("2020-07-01", Optional.of("2020-07-01T00:00:00.000+0000")),
      Arguments.of("10/12/2020+03:00", Optional.of("2020-12-10T03:00:00.000+0000")),
      Arguments.of("2020-07-16T10:14:22.761421", Optional.of("2020-07-16T10:14:22.761+0000")),
      Arguments.of("2019-06-15T12:00:00.000+02:00", Optional.of("2019-06-15T12:00:00.000+0200")),
      Arguments.of("2019-06-15T12:00:00.000+0200", Optional.of("2019-06-15T12:00:00.000+0200")),
      Arguments.of("SOME-NON-PARSEABLE_DATE", Optional.empty())
    );
  }
}
