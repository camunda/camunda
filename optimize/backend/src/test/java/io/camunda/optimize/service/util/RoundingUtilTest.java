/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class RoundingUtilTest {

  @ParameterizedTest
  @MethodSource("roundDownScenarios")
  public void testRoundDownToNearestPowerOfTen(final double input, final double expectedOutput) {
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
        Arguments.of(11.0D, 10.0D));
  }

  @ParameterizedTest
  @MethodSource("roundUpScenarios")
  public void testRoundUpToNearestPowerOfTen(final double input, final double expectedOutput) {
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
        Arguments.of(11.0D, 100.0D));
  }
}
