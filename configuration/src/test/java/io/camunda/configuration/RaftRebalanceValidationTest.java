/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Raft.Rebalance;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

final class RaftRebalanceValidationTest {

  @Test
  void shouldRejectNegativeReplicationLagThreshold() {
    // given
    final var rebalance = new Rebalance();

    // when / then
    assertThatThrownBy(() -> rebalance.setReplicationLagThreshold(DataSize.ofBytes(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAcceptZeroReplicationLagThreshold() {
    // given
    final var rebalance = new Rebalance();

    // when / then
    assertThatCode(() -> rebalance.setReplicationLagThreshold(DataSize.ofBytes(0)))
        .doesNotThrowAnyException();
    assertThat(rebalance.getReplicationLagThreshold()).isEqualTo(DataSize.ofBytes(0));
  }

  @Test
  void shouldRejectZeroReplicationTimeout() {
    // given
    final var rebalance = new Rebalance();

    // when / then
    assertThatThrownBy(() -> rebalance.setReplicationTimeout(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNegativeReplicationTimeout() {
    // given
    final var rebalance = new Rebalance();

    // when / then
    assertThatThrownBy(() -> rebalance.setReplicationTimeout(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNonPositiveMaxTransferAttempts() {
    // given
    final var rebalance = new Rebalance();

    // when / then
    assertThatThrownBy(() -> rebalance.setMaxTransferAttempts(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rebalance.setMaxTransferAttempts(-3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAcceptValidValues() {
    // given
    final var rebalance = new Rebalance();

    // when / then
    assertThatCode(
            () -> {
              rebalance.setReplicationLagThreshold(DataSize.ofMegabytes(16));
              rebalance.setReplicationTimeout(Duration.ofSeconds(30));
              rebalance.setMaxTransferAttempts(5);
            })
        .doesNotThrowAnyException();
  }
}
