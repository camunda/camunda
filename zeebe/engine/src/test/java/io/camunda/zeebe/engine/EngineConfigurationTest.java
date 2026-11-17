/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class EngineConfigurationTest {

  @ParameterizedTest
  @MethodSource("validFixedStorageOrdinals")
  void shouldAcceptValidFixedStorageOrdinals(final List<Integer> fixedStorageOrdinals) {
    // given
    final var configuration = new EngineConfiguration();

    // when
    configuration.setFixedStorageOrdinals(fixedStorageOrdinals);

    // then
    assertThat(configuration.getFixedStorageOrdinals())
        .containsExactlyElementsOf(fixedStorageOrdinals);
  }

  @ParameterizedTest
  @MethodSource("invalidFixedStorageOrdinals")
  void shouldRejectInvalidFixedStorageOrdinals(final List<Integer> fixedStorageOrdinals) {
    // given
    final var configuration = new EngineConfiguration();

    // when / then
    assertThatThrownBy(() -> configuration.setFixedStorageOrdinals(fixedStorageOrdinals))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(configuration.getFixedStorageOrdinals())
        .isEqualTo(EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_FIXED_STORAGE_ORDINALS);
  }

  private static Stream<Arguments> validFixedStorageOrdinals() {
    return Stream.of(
        Arguments.of(List.of(-1)),
        Arguments.of(List.of(0)),
        Arguments.of(List.of(7)),
        Arguments.of(List.of(1001, 1002)));
  }

  private static Stream<Arguments> invalidFixedStorageOrdinals() {
    return Stream.of(
        Arguments.of((List<Integer>) null),
        Arguments.of(List.of()),
        Arguments.of(List.of(-2)),
        Arguments.of(Arrays.asList((Integer) null)),
        Arguments.of(List.of(-1, 5)),
        Arguments.of(List.of(0, 5)),
        Arguments.of(Arrays.asList(1, null)));
  }
}
