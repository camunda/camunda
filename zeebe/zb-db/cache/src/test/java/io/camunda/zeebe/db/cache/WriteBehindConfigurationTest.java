/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class WriteBehindConfigurationTest {

  @Test
  void shouldAcceptZeroPendingBytesAndZeroWriteDelay() {
    assertThatCode(
            () -> new TestConfiguration().maxPendingBytes(0).maxWriteDelay(Duration.ZERO).build())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectNegativeMaxPendingBytes() {
    assertThatThrownBy(() -> new TestConfiguration().maxPendingBytes(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxPendingBytes");
  }

  @Test
  void shouldRejectCacheWithoutBytes() {
    assertThatThrownBy(() -> new TestConfiguration().cacheMaxBytes(0).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cacheMaxBytes");
  }

  @Test
  void shouldRejectBatchWithoutKeys() {
    assertThatThrownBy(() -> new TestConfiguration().batchSize(0).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize");
  }

  @Test
  void shouldRejectNegativeWriteDelay() {
    assertThatThrownBy(() -> new TestConfiguration().maxWriteDelay(Duration.ofMillis(-1)).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxWriteDelay");
  }

  @Test
  void shouldRejectWriteDelayBeyondNanoseconds() {
    assertThatThrownBy(
            () -> new TestConfiguration().maxWriteDelay(Duration.ofDays(200_000)).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxWriteDelay");
  }

  @Test
  void shouldRejectSnapshotCatchUpWithoutTime() {
    assertThatThrownBy(() -> new TestConfiguration().snapshotCatchUpTimeout(Duration.ZERO).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("snapshotCatchUpTimeout");
  }
}
