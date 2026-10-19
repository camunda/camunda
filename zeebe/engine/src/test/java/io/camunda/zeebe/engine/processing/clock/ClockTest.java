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
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
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

  @After
  public void afterEach() {
    ENGINE.getClock().reset();
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
    // awaitProcessingOf only awaits the pin command's own processing, not that the state machine
    // has fully caught up on the log, so this must be polled rather than asserted once
    Awaitility.await("until the engine has reached the end of the log after pinning")
        .until(ENGINE::hasReachedEnd);

    // when
    ENGINE.snapshot();
    ENGINE.stop();
    RecordingExporter.reset();
    ENGINE.start();
    // starting the engine only awaits recovery, not that the state machine has caught up on
    // resumed processing, so this must be polled rather than asserted once
    Awaitility.await("until the engine has reached the end of the log after restart")
        .until(ENGINE::hasReachedEnd);

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

  @Test
  public void shouldFetchPinnedTime() {
    // given
    final var fakeNow = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);
    ENGINE.awaitProcessingOf(clockClient.pinAt(fakeNow));

    // when
    final var record = clockClient.fetch();

    // then
    Assertions.assertThat(record).hasIntent(ClockIntent.FETCHED);
    assertThat(record.getValue().getTime()).isEqualTo(fakeNow.toEpochMilli());
  }

  @Test
  public void shouldFetchActorClockTimeWhenClockIsNotModified() {
    // given
    ENGINE.awaitProcessingOf(clockClient.reset());
    final var actorClockTime = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
    ENGINE.getClock().setCurrentTime(actorClockTime);

    // when
    final var record = clockClient.fetch();

    // then
    Assertions.assertThat(record).hasIntent(ClockIntent.FETCHED);
    assertThat(record.getValue().getTime()).isEqualTo(actorClockTime.toEpochMilli());
  }

  @Test
  public void shouldNotModifyClockOnFetch() {
    // given
    final var fakeNow = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);
    ENGINE.awaitProcessingOf(clockClient.pinAt(fakeNow));

    // when
    ENGINE.awaitProcessingOf(clockClient.fetch());

    // then
    assertThat(ENGINE.getStreamClock().instant()).isEqualTo(fakeNow);
    assertThat(ENGINE.getProcessingState().getClockState().getModification())
        .isEqualTo(Modification.pinAt(fakeNow));
  }

  @Test
  public void shouldRespondToFetchClock() {
    // given
    final var fakeNow = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);
    ENGINE.awaitProcessingOf(clockClient.pinAt(fakeNow));

    // when
    ENGINE.awaitProcessingOf(clockClient.requestId(1L).requestStreamId(1).fetch());

    // then
    final var inOrder = Mockito.inOrder(ENGINE.getCommandResponseWriter());
    inOrder
        .verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .intent(ClockIntent.FETCHED);
    inOrder
        .verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .tryWriteResponse(1, 1L);
  }

  @Test
  public void shouldOffsetClock() {
    // given
    final var pinnedAt = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);
    ENGINE.awaitProcessingOf(clockClient.pinAt(pinnedAt));
    final var actorClockTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    ENGINE.getClock().setCurrentTime(actorClockTime);
    final var offset = Duration.ofMinutes(10);

    // when
    final var record = clockClient.offsetBy(offset);

    // then
    Assertions.assertThat(record).hasIntent(ClockIntent.OFFSETTED);
    assertThat(record.getValue().getTime()).isEqualTo(offset.toMillis());
    assertThat(clockClient.fetch().getValue().getTime())
        .describedAs("the offset replaces the pin and the clock runs from the system time")
        .isEqualTo(actorClockTime.plus(offset).toEpochMilli());
  }

  @Test
  public void shouldRespondToOffsetClock() {
    // when
    ENGINE.awaitProcessingOf(
        clockClient.requestId(1L).requestStreamId(1).offsetBy(Duration.ofMinutes(10)));

    // then
    final var inOrder = Mockito.inOrder(ENGINE.getCommandResponseWriter());
    inOrder
        .verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .intent(ClockIntent.OFFSETTED);
    inOrder
        .verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .tryWriteResponse(1, 1L);
  }

  @Test
  public void shouldAddOffsetToClock() {
    // given
    ENGINE.awaitProcessingOf(clockClient.reset());
    final var actorClockTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    ENGINE.getClock().setCurrentTime(actorClockTime);
    final var offset = Duration.ofHours(1);

    // when
    final var record = clockClient.add(offset);

    // then
    Assertions.assertThat(record).hasIntent(ClockIntent.OFFSETTED);
    assertThat(record.getValue().getTime()).isEqualTo(offset.toMillis());
    assertThat(clockClient.fetch().getValue().getTime())
        .isEqualTo(actorClockTime.plus(offset).toEpochMilli());
  }

  @Test
  public void shouldAddOffsetOnTopOfCurrentOffset() {
    // given
    ENGINE.awaitProcessingOf(clockClient.reset());
    final var actorClockTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    ENGINE.getClock().setCurrentTime(actorClockTime);
    clockClient.add(Duration.ofHours(1));

    // when
    final var record = clockClient.add(Duration.ofMinutes(30));

    // then
    final var totalOffset = Duration.ofMinutes(90);
    Assertions.assertThat(record).hasIntent(ClockIntent.OFFSETTED);
    assertThat(record.getValue().getTime()).isEqualTo(totalOffset.toMillis());
    assertThat(clockClient.fetch().getValue().getTime())
        .isEqualTo(actorClockTime.plus(totalOffset).toEpochMilli());
  }

  @Test
  public void shouldAddOnTopOfStoredOffsetWhenClockIsNotUpdatedYet() {
    // given
    ENGINE.awaitProcessingOf(clockClient.reset());
    final var actorClockTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    ENGINE.getClock().setCurrentTime(actorClockTime);
    clockClient.add(Duration.ofHours(1));
    final var streamClock = (ControllableStreamClock) ENGINE.getStreamClock();
    Awaitility.await("until the clock is moved by the first add")
        .until(
            streamClock::currentModification, Modification.offsetBy(Duration.ofHours(1))::equals);
    // the clock is only updated after the change is committed, so it can be behind the state
    streamClock.reset();

    // when
    ENGINE.awaitProcessingOf(
        clockClient.requestId(1L).requestStreamId(1).add(Duration.ofMinutes(30)));

    // then
    final var totalOffset = Duration.ofMinutes(90);
    final var responseValue = ArgumentCaptor.forClass(BufferWriter.class);
    verify(ENGINE.getCommandResponseWriter(), timeout(1000)).valueWriter(responseValue.capture());
    assertThat(toClockRecord(responseValue.getValue()).getTime())
        .describedAs("the new clock time in the response")
        .isEqualTo(actorClockTime.plus(totalOffset).toEpochMilli());
    Awaitility.await("until the clock is moved by the second add")
        .untilAsserted(
            () ->
                assertThat(streamClock.currentModification())
                    .isEqualTo(Modification.offsetBy(totalOffset)));
  }

  @Test
  public void shouldAddNegativeOffsetToClock() {
    // given
    ENGINE.awaitProcessingOf(clockClient.reset());
    final var actorClockTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    ENGINE.getClock().setCurrentTime(actorClockTime);
    final var offset = Duration.ofHours(-1);

    // when
    final var record = clockClient.add(offset);

    // then
    Assertions.assertThat(record).hasIntent(ClockIntent.OFFSETTED);
    assertThat(record.getValue().getTime()).isEqualTo(offset.toMillis());
    assertThat(clockClient.fetch().getValue().getTime())
        .isEqualTo(actorClockTime.plus(offset).toEpochMilli());
  }

  @Test
  public void shouldKeepClockPinnedWhenAddingOffset() {
    // given
    final var pinnedAt = Instant.now().minusSeconds(180).truncatedTo(ChronoUnit.MILLIS);
    ENGINE.awaitProcessingOf(clockClient.pinAt(pinnedAt));
    final var offset = Duration.ofDays(1);

    // when
    final var record = clockClient.add(offset);

    // then
    Assertions.assertThat(record).hasIntent(ClockIntent.PINNED);
    assertThat(record.getValue().getTime()).isEqualTo(pinnedAt.plus(offset).toEpochMilli());
    assertThat(clockClient.fetch().getValue().getTime())
        .isEqualTo(pinnedAt.plus(offset).toEpochMilli());
  }

  @Test
  public void shouldRejectAddWhenNewTimeIsNegative() {
    // given
    ENGINE.awaitProcessingOf(clockClient.pinAt(1_000));

    // when
    final var record = clockClient.expectRejection().add(-2_000);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClockIntent.ADD)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected the new clock time to be not negative, but adding -2000 ms to the current"
                + " time 1000 makes it -1000");
    assertThat(ENGINE.clock().fetch().getValue().getTime()).isEqualTo(1_000);
  }

  @Test
  public void shouldRejectAddWhenNewTimeIsTooLarge() {
    // given
    ENGINE.awaitProcessingOf(clockClient.pinAt(1_000));

    // when
    final var record = clockClient.expectRejection().add(Long.MAX_VALUE);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClockIntent.ADD)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected the new clock time to be at most 9223372036854775807, but adding"
                + " 9223372036854775807 ms to the current time 1000 goes beyond that");
    assertThat(ENGINE.clock().fetch().getValue().getTime()).isEqualTo(1_000);
  }

  @Test
  public void shouldRespondWithNewTimeToAddClock() {
    // given
    ENGINE.awaitProcessingOf(clockClient.reset());
    final var actorClockTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    ENGINE.getClock().setCurrentTime(actorClockTime);
    final var offset = Duration.ofHours(1);

    // when
    ENGINE.awaitProcessingOf(clockClient.requestId(1L).requestStreamId(1).add(offset));

    // then
    final var responseWriter = ENGINE.getCommandResponseWriter();
    final var inOrder = Mockito.inOrder(responseWriter);
    inOrder.verify(responseWriter, timeout(1000).times(1)).intent(ClockIntent.OFFSETTED);
    inOrder.verify(responseWriter, timeout(1000).times(1)).tryWriteResponse(1, 1L);

    final var responseValue = ArgumentCaptor.forClass(BufferWriter.class);
    verify(responseWriter).valueWriter(responseValue.capture());
    assertThat(toClockRecord(responseValue.getValue()).getTime())
        .describedAs("the response holds the new clock time, not the total offset")
        .isEqualTo(actorClockTime.plus(offset).toEpochMilli());
  }

  private static ClockRecord toClockRecord(final BufferWriter writer) {
    final var buffer = new UnsafeBuffer(new byte[writer.getLength()]);
    writer.write(buffer, 0);
    final var record = new ClockRecord();
    record.wrap(buffer);
    return record;
  }
}
