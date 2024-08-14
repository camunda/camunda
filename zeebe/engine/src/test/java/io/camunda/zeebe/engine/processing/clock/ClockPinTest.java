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
import io.camunda.zeebe.engine.util.client.ClockClient;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ClockPinTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final ClockClient clockClient = ENGINE.clock();

  @Test
  public void shouldResetClock() {
    // given
    final var fakeNow = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);

    // when
    final var record = clockClient.pinAt(fakeNow);
    // required to ensure we apply the side effect of the clock
    ENGINE.awaitProcessingOf(record);

    // then
    assertThat(ENGINE.getStreamClock().instant()).isEqualTo(fakeNow);
    assertThat(ENGINE.getProcessingState().getClockState().getModification())
        .isEqualTo(Modification.pinAt(fakeNow));
  }
}
