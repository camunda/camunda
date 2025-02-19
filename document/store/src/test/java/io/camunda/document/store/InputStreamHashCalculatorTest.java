/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class InputStreamHashCalculatorTest {

  @ParameterizedTest
  @MethodSource("provideInputAndActualHash")
  void testHashCalculation(final String input, final String actualHash) throws Exception {
    // given
    final var stream = new ByteArrayInputStream(input.getBytes());

    // when
    final var result = InputStreamHashCalculator.streamAndCalculateHash(stream);

    // then
    assertThat(result).isEqualTo(actualHash);
  }

  static Stream<Arguments> provideInputAndActualHash() {
    return Stream.of(
        Arguments.of(
            "Hello, World", "03675ac53ff9cd1535ccc7dfcdfa2c458c5218371f418dc136f2d19ac1fbe8a5"),
        Arguments.of("", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Arguments.of(
            "I don't\nknow\nmuch",
            "9a1ef4dee4df717c905b341a6bc9ba338a25aec9492c7bd96ccc2306f18be7ca"),
        Arguments.of(
            "last test I promise",
            "763e43816c8efe27f0021050a897a3749ecb49a0720f502c11f2ec407a9e378e"));
  }
}
