/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class RoundingUtilTest {

  @ParameterizedTest
  @MethodSource("roundDownScenarios")
  public void testRoundDownToNearestPowerOfTen(double input, double expectedOutput) {
    assertThat(RoundingUtil.roundDownToNearestPowerOfTen(input)).isEqualTo(expectedOutput);
  }

  private static Stream<Arguments> roundDownScenarios() {
    return Stream.of(
      Arguments.of(-11.0D, -100.0D),
      Arguments.of(-5.0D, -10.0D),
      Arguments.of(-1.1D, -10.0D),
      Arguments.of(0.0D, 0.0D),
      Arguments.of(1.1D, 1.0D),
      Arguments.of(5.0D, 1.0D),
      Arguments.of(11.0D, 10.0D)
    );
  }

  @ParameterizedTest
  @MethodSource("roundUpScenarios")
  public void testRoundUpToNearestPowerOfTen(double input, double expectedOutput) {
    assertThat(RoundingUtil.roundUpToNearestPowerOfTen(input)).isEqualTo(expectedOutput);
  }

  private static Stream<Arguments> roundUpScenarios() {
    return Stream.of(
      Arguments.of(-11.0D, -10.0D),
      Arguments.of(-5.0D, -1.0D),
      Arguments.of(-1.1D, -1.0D),
      Arguments.of(0.0D, 0.0D),
      Arguments.of(1.1D, 10.0D),
      Arguments.of(5.0D, 10.0D),
      Arguments.of(11.0D, 100.0D)
    );
  }

}
