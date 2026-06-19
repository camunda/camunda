/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.util.stream.FakeProcessingResultBuilder;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the engine command writer's ECV gate. The deck's "enforced, not assumed" rule
 * applies both to admission (broker) and to internal follow-up commands (engine command writer): if
 * a processor tries to emit a command its current cluster version doesn't support, the writer
 * throws.
 */
final class CommandWriterGateTest {

  @Test
  void shouldThrow_whenProcessorEmitsCommandGatedAboveCurrentEcv() {
    // given a writer pinned to active ECV (0, 0)
    final var resultBuilder = new FakeProcessingResultBuilder<>();
    final var writers = new Writers(() -> resultBuilder, mock(EventAppliers.class));
    writers.setActiveClusterVersionProvider(() -> 0);

    // when a processor tries to emit PING (gated by the catalog at (810, 10))
    final var record = new ClusterVersionRecord().setGatedField("x");

    // then the writer refuses — the command never reaches the result builder
    assertThatThrownBy(() -> writers.command().appendNewCommand(ClusterVersionIntent.PING, record))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Refusing to emit command PING")
        .hasMessageContaining("requires cluster version ordinal 10")
        .hasMessageContaining("current active ordinal is 0");
  }

  @Test
  void shouldAdmit_whenActiveEcvSatisfiesRequirement() {
    final var resultBuilder = new FakeProcessingResultBuilder<>();
    final var writers = new Writers(() -> resultBuilder, mock(EventAppliers.class));
    writers.setActiveClusterVersionProvider(() -> 10);

    final var record = new ClusterVersionRecord().setGatedField("x");
    writers.command().appendNewCommand(ClusterVersionIntent.PING, record);

    // command reached the result builder
    assertThat(resultBuilder.getFollowupRecords()).hasSize(1);
  }

  @Test
  void shouldAdmit_whenCommandIsNotGated() {
    final var resultBuilder = new FakeProcessingResultBuilder<>();
    final var writers = new Writers(() -> resultBuilder, mock(EventAppliers.class));
    writers.setActiveClusterVersionProvider(() -> 0);

    // RAISE is not in the catalog as a gated command — it must always be admissible.
    final var record = new ClusterVersionRecord().setLine(810).setOrdinal(2);
    writers.command().appendNewCommand(ClusterVersionIntent.RAISE, record);

    assertThat(resultBuilder.getFollowupRecords()).hasSize(1);
  }
}
