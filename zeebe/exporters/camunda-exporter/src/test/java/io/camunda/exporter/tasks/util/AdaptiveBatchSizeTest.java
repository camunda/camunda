/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class AdaptiveBatchSizeTest {

  @Test
  void shouldStartAtTheConfiguredSize() {
    // given / when
    final var batchSize = new AdaptiveBatchSize(100);

    // then
    assertThat(batchSize.current()).isEqualTo(100);
    assertThat(batchSize.configured()).isEqualTo(100);
  }

  @Test
  void shouldHalve() {
    // given
    final var batchSize = new AdaptiveBatchSize(100);

    // when
    final var halved = batchSize.halve();

    // then
    assertThat(halved).isTrue();
    assertThat(batchSize.current()).isEqualTo(50);
  }

  @Test
  void shouldKeepHalvingOnEveryCall() {
    // given
    final var batchSize = new AdaptiveBatchSize(100);

    // when
    batchSize.halve();
    batchSize.halve();
    batchSize.halve();

    // then
    assertThat(batchSize.current()).isEqualTo(12);
  }

  @Test
  void shouldRefuseToHalveBelowOne() {
    // given
    final var batchSize = new AdaptiveBatchSize(1);

    // when
    final var halved = batchSize.halve();

    // then - the caller has to handle a write it cannot make any smaller
    assertThat(halved).isFalse();
    assertThat(batchSize.current()).isEqualTo(1);
  }

  @Test
  void shouldReturnToTheConfiguredSizeOnReset() {
    // given
    final var batchSize = new AdaptiveBatchSize(100);
    batchSize.halve();
    batchSize.halve();

    // when
    batchSize.reset();

    // then
    assertThat(batchSize.current()).isEqualTo(100);
  }
}
