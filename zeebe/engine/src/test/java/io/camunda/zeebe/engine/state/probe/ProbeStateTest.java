/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ProbeStateTest {

  private MutableProcessingState processingState;
  private MutableProbeState probeState;

  @BeforeEach
  void setUp() {
    probeState = processingState.getProbeState();
  }

  @Test
  void shouldCreateAnActivatableProbe() {
    // when
    probeState.create(1L, probe("probe-a", 1_000));

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.ACTIVATABLE);
    assertThat(probeState.getProbe(1L))
        .isNotNull()
        .satisfies(
            probe -> {
              assertThat(probe.getProbeId()).isEqualTo("probe-a");
              assertThat(probe.getDeadline()).isEqualTo(1_000);
            });
  }

  @Test
  void shouldRemoveTheProbeAndItsDeadline() {
    // given
    probeState.create(1L, probe("probe-a", 1_000));

    // when
    probeState.remove(1L);

    // then
    assertThat(probeState.getState(1L)).isEqualTo(State.NOT_FOUND);
    assertThat(probeState.getProbe(1L)).isNull();
    assertThat(probeState.forEachTimedOutProbe(Long.MAX_VALUE, (key, probe) -> true)).isEqualTo(-1);
  }

  @Test
  void shouldVisitTimedOutProbesEarliestFirstAndReturnTheNextDeadline() {
    // given
    probeState.create(1L, probe("probe-a", 3_000));
    probeState.create(2L, probe("probe-b", 1_000));
    probeState.create(3L, probe("probe-c", 2_000));
    final List<Long> visited = new ArrayList<>();

    // when
    final long nextDeadline =
        probeState.forEachTimedOutProbe(
            2_000,
            (key, probe) -> {
              visited.add(key);
              return true;
            });

    // then
    assertThat(visited).containsExactly(2L, 3L);
    assertThat(nextDeadline).isEqualTo(3_000);
  }

  private static ProbeRecord probe(final String probeId, final long deadline) {
    return new ProbeRecord().setProbeId(probeId).setInputExpression("={}").setDeadline(deadline);
  }
}
