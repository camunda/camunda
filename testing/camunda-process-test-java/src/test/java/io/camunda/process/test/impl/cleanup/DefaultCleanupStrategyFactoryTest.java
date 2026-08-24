/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.api.DataDeletionMode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultCleanupStrategyFactoryTest {

  private final DefaultCleanupStrategyFactory factory = new DefaultCleanupStrategyFactory();

  @ParameterizedTest
  @MethodSource("dataDeletionModes")
  void shouldCreateCleanupStrategyForDataDeletionMode(
      final DataDeletionMode dataDeletionMode,
      final Class<? extends CleanupStrategy> expectedType) {
    // given

    // when
    final CleanupStrategy strategy = factory.create(dataDeletionMode);

    // then
    assertThat(strategy).isInstanceOf(expectedType);
  }

  @Test
  void shouldCreateNoneCleanupStrategyIfDeletionModeIsNull() {
    // given

    // when
    final CleanupStrategy strategy = factory.create(null);

    // then
    assertThat(strategy).isInstanceOf(NoneCleanupStrategy.class);
  }

  private static Stream<Arguments> dataDeletionModes() {
    return Stream.of(
        Arguments.of(DataDeletionMode.CLUSTER_PURGE, ClusterPurgeCleanupStrategy.class),
        Arguments.of(DataDeletionMode.NONE, NoneCleanupStrategy.class));
  }
}
