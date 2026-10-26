/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Rule;
import org.junit.Test;

/**
 * After a restart, the engine rebuilds its state from the last snapshot and the events written
 * after it. The running clock must match the rebuilt state, not the snapshot. Each test restarts
 * the engine, so each gets its own.
 */
public final class ClockRecoveryTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldKeepAddedTimeAfterRestart() {
    // given
    engine.snapshot();
    engine.awaitProcessingOf(engine.clock().add(Duration.ofHours(1)));

    // when
    restartFromSnapshot();

    // then
    assertClockAfterRestart(Modification.offsetBy(Duration.ofHours(1)));
  }

  @Test
  public void shouldKeepPinnedTimeAfterRestart() {
    // given
    final var pinnedAt = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);
    engine.snapshot();
    engine.awaitProcessingOf(engine.clock().pinAt(pinnedAt));

    // when
    restartFromSnapshot();

    // then
    assertClockAfterRestart(Modification.pinAt(pinnedAt));
  }

  @Test
  public void shouldKeepResetAfterRestart() {
    // given
    engine.awaitProcessingOf(engine.clock().pinAt(Instant.now().minus(Duration.ofDays(1))));
    engine.snapshot();
    engine.awaitProcessingOf(engine.clock().reset());

    // when
    restartFromSnapshot();

    // then
    assertClockAfterRestart(Modification.none());
  }

  private void restartFromSnapshot() {
    engine.stop();
    engine.start();
  }

  private void assertClockAfterRestart(final Modification expected) {
    assertThat(engine.getProcessingState().getClockState().getModification())
        .describedAs("the stored clock after the restart")
        .isEqualTo(expected);
    assertThat(((ControllableStreamClock) engine.getStreamClock()).currentModification())
        .describedAs("the running clock after the restart")
        .isEqualTo(expected);
  }
}
