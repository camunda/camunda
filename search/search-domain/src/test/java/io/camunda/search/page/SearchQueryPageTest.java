/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SearchQueryPageTest {

  @ParameterizedTest
  @MethodSource
  void sanitize(
      final Integer from, final Integer size, final Integer targetFrom, final Integer targetSize) {
    final var page = new SearchQueryPage.Builder().from(from).size(size).build();

    assertThat(page.from()).isEqualTo(targetFrom);
    assertThat(page.size()).isEqualTo(targetSize);
  }

  private static Stream<Arguments> sanitize() {
    return Stream.of(
        Arguments.arguments(0, 20, 0, 20),
        Arguments.arguments(0, 20, 0, 20),
        Arguments.arguments(0, 2000, 0, 2000),
        Arguments.arguments(-100, 20, 0, 20),
        Arguments.arguments(null, 20, 0, 20),
        Arguments.arguments(0, null, 0, 100),
        Arguments.arguments(null, null, 0, 100));
  }
}
