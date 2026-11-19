/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class KeyVaultRetryStrategyTest {

  @Test
  void shouldSpreadTheBackoffOfConcurrentReadsWithinItsDelay() {
    // given the default 5s call timeout and three retries: a 357ms first delay
    final var strategy = new KeyVaultRetryStrategy(3, Duration.ofSeconds(5));

    // when
    final var delays =
        IntStream.range(0, 100).mapToObj(i -> strategy.calculateRetryDelay(0)).toList();

    // then — drawn between half and all of the delay, rather than all alike
    assertThat(delays)
        .allSatisfy(
            delay -> assertThat(delay).isBetween(Duration.ofMillis(179), Duration.ofMillis(357)));
    assertThat(delays.stream().distinct().count()).isGreaterThan(1);
  }
}
