/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class WriteBehindConfigurationTest {

  @Test
  void shouldAcceptDefaults() {
    assertThat(WriteBehindConfiguration.defaults()).isNotNull();
  }

  @Test
  void shouldAcceptZeroDelayThresholdAndCache() {
    // when
    final var config =
        new WriteBehindConfiguration(1, 0, 0, Duration.ofSeconds(1), Duration.ZERO, 0);

    // then
    assertThat(config.flushDelay()).isZero();
  }

  @Test
  void shouldRejectMaxPendingBytesThatCommitsAlwaysExceed() {
    // a limit below zero would make the first commit wait for a flush of an empty queue
    assertThatThrownBy(() -> WriteBehindConfiguration.defaults().withMaxPendingBytes(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxPendingBytes");
    assertThatThrownBy(() -> WriteBehindConfiguration.defaults().withMaxPendingBytes(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxPendingBytes");
  }

  @Test
  void shouldRejectNegativeCacheSize() {
    assertThatThrownBy(() -> WriteBehindConfiguration.defaults().withCacheMaxBytes(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cacheMaxBytes");
  }

  @Test
  void shouldRejectNegativeFlushDelay() {
    assertThatThrownBy(
            () -> WriteBehindConfiguration.defaults().withFlushDelay(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flushDelay");
  }

  @Test
  void shouldRejectNegativeFlushThreshold() {
    assertThatThrownBy(() -> WriteBehindConfiguration.defaults().withFlushThresholdBytes(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flushThresholdBytes");
  }

  @Test
  void shouldRejectNegativeWriteRetries() {
    assertThatThrownBy(
            () -> new WriteBehindConfiguration(1, 0, -1, Duration.ofSeconds(1), Duration.ZERO, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxWriteRetries");
  }

  @Test
  void shouldRejectFlushTimeoutThatIsNotPositive() {
    assertThatThrownBy(() -> new WriteBehindConfiguration(1, 0, 0, Duration.ZERO, Duration.ZERO, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flushTimeout");
  }
}
