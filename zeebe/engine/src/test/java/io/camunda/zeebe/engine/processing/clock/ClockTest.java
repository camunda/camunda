/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.ClockClient;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class ClockTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final ClockClient clockClient = ENGINE.clock();

  @Before
  public void beforeEach() {
    RecordingExporter.reset();
    clearInvocations(ENGINE.getCommandResponseWriter());
  }

  @Test
  public void shouldPinClock() {
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
    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(0))
        .tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldRejectClockPinWithNegativeTimestamp() {
    // when
    final var record = clockClient.expectRejection().pinAt(-1);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClockIntent.PIN)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Expected pin time to be not negative but it was -1");
  }

  @Test
  public void shouldRespondToPinClock() {
    // given
    final var fakeNow = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);

    // when
    final var record = clockClient.requestId(1L).requestStreamId(1).pinAt(fakeNow);
    // required to ensure we apply the side effect of the clock
    ENGINE.awaitProcessingOf(record);

    // then
    final var inOrder = Mockito.inOrder(ENGINE.getCommandResponseWriter());
    inOrder
        .verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .intent(ClockIntent.PINNED);
    inOrder
        .verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .tryWriteResponse(1, 1L);
  }

  @Test
  public void shouldRestorePinOnRestart() {
    // given
    final var fakeNow = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);
    final var record = clockClient.pinAt(fakeNow);
    // required to ensure we have updated the state
    ENGINE.awaitProcessingOf(record);
    assertThat(ENGINE.hasReachedEnd()).isTrue();

    // when
    ENGINE.snapshot();
    ENGINE.stop();
    RecordingExporter.reset();
    ENGINE.start();
    assertThat(ENGINE.hasReachedEnd()).isTrue();

    // then
    assertThat(ENGINE.getStreamClock().instant()).isEqualTo(fakeNow);
    assertThat(ENGINE.getProcessingState().getClockState().getModification())
        .isEqualTo(Modification.pinAt(fakeNow));
  }

  @Test
  public void shouldResetClock() {
    // given
    final var fakeNow = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);
    ENGINE.awaitProcessingOf(clockClient.pinAt(fakeNow));

    // when
    final var record = clockClient.reset();
    // required to ensure we apply the side effect of the clock
    ENGINE.awaitProcessingOf(record);

    // then
    assertThat(ENGINE.getStreamClock().instant()).isAfter(fakeNow);
    assertThat(ENGINE.getProcessingState().getClockState().getModification())
        .isEqualTo(Modification.none());
    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(0))
        .tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldRespondToResetClock() {
    // given
    final var fakeNow = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);
    ENGINE.awaitProcessingOf(clockClient.pinAt(fakeNow));

    // when
    final var record = clockClient.requestId(1L).requestStreamId(1).reset();
    // required to ensure we apply the side effect of the clock
    ENGINE.awaitProcessingOf(record);

    // then
    final var inOrder = Mockito.inOrder(ENGINE.getCommandResponseWriter());
    inOrder
        .verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .intent(ClockIntent.RESETTED);
    inOrder
        .verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .tryWriteResponse(1, 1L);
  }
}
