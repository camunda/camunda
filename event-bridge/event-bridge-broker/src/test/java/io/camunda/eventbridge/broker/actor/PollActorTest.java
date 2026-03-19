/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.broker.actor.PollActor.InvalidPositionException;
import io.camunda.eventbridge.broker.actor.PollActor.PollResult;
import io.camunda.eventbridge.broker.actor.PollActor.PositionTruncatedException;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.time.Duration;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link PollActor}.
 *
 * <p>Covers happy-path reading, long-poll park/wake, truncation detection, invalid-position
 * rejection, connect/disconnect lifecycle, and the case where log-record-available notifications
 * fire while parked polls are waiting.
 */
class PollActorTest {

  private static final int PARTITION_ID = 0;
  private static final EventBridgeProperties PROPERTIES =
      new EventBridgeProperties(null, null, null, null, null, null, null, null);

  private ActorScheduler scheduler;
  private LogStream logStream;
  private LogStreamReader reader;
  private PollActor actor;

  @BeforeEach
  void setUp() {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("test-scheduler")
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();

    reader = mock(LogStreamReader.class);
    logStream = mock(LogStream.class);
    when(logStream.newLogStreamReader()).thenReturn(reader);

    actor = new PollActor(PARTITION_ID, PROPERTIES);
    scheduler.submitActor(actor).join();
    // Connect to the mock LogStream
    actor.connect(logStream).join();
  }

  @AfterEach
  void tearDown() throws Exception {
    actor.closeAsync().join();
    scheduler.close();
  }

  // ---------------------------------------------------------------------------
  // Helper: build a mock LoggedEvent with a raw payload stored as a msgpack binary value.
  // We encode the payload using the same format that RawEventRecordValue.write() produces.

  private static LoggedEvent mockEvent(final long position, final byte[] rawPayload) {
    // Build a msgpack-binary-encoded buffer for the given raw payload:
    // bin8 header (0xc4, length-byte) + payload bytes (for payloads ≤ 255 bytes)
    final byte[] encoded = new byte[2 + rawPayload.length];
    encoded[0] = (byte) 0xc4; // msgpack bin8 type tag
    encoded[1] = (byte) rawPayload.length;
    System.arraycopy(rawPayload, 0, encoded, 2, rawPayload.length);
    final var buf = new UnsafeBuffer(encoded);

    final var event = mock(LoggedEvent.class);
    when(event.getPosition()).thenReturn(position);
    // readValue(BufferReader) calls reader.wrap(buffer, offset, length)
    // Since the reader is RawEventRecordValue (a BufferReader), we configure
    // the mock to invoke wrap() on it with our encoded buffer.
    org.mockito.Mockito.doAnswer(
            inv -> {
              final io.camunda.zeebe.util.buffer.BufferReader br = inv.getArgument(0);
              br.wrap(buf, 0, buf.capacity());
              return null;
            })
        .when(event)
        .readValue(any());
    return event;
  }

  // ---------------------------------------------------------------------------
  // Poll: immediate read (records available)

  @Nested
  class PollImmediate {

    @Test
    void shouldReturnEventsFromPosition() {
      // given — two events at positions 1001, 1002
      final byte[] payload1 = {1, 2, 3};
      final byte[] payload2 = {4, 5};
      final var event1 = mockEvent(1001L, payload1);
      final var event2 = mockEvent(1002L, payload2);
      when(reader.seek(1001L)).thenReturn(true);
      when(reader.hasNext()).thenReturn(true, true, false);
      when(reader.next()).thenReturn(event1, event2);
      // when
      final PollResult result = actor.poll(1001L, 10, 0).join();

      // then
      assertThat(result.events()).hasSize(2);
      assertThat(result.events().get(0).position()).isEqualTo(1001L);
      assertThat(result.events().get(0).payload()).containsExactly(1, 2, 3);
      assertThat(result.events().get(1).position()).isEqualTo(1002L);
      assertThat(result.events().get(1).payload()).containsExactly(4, 5);
    }

    @Test
    void shouldRespectMaxRecordsLimit() {
      // given — 3 events but maxRecords = 2
      final var event1 = mockEvent(1L, new byte[] {1});
      final var event2 = mockEvent(2L, new byte[] {2});
      when(reader.seek(1L)).thenReturn(true);
      when(reader.hasNext())
          .thenReturn(true, true, true); // would be true a 3rd time but maxRecords stops it
      when(reader.next()).thenReturn(event1, event2);

      // when
      final PollResult result = actor.poll(1L, 2, 0).join();

      // then
      assertThat(result.events()).hasSize(2);
    }

    @Test
    void shouldSetNextPositionToLastEventPlusOne() {
      // given
      final var event = mockEvent(100L, new byte[] {7});
      when(reader.seek(100L)).thenReturn(true);
      when(reader.hasNext()).thenReturn(true, false);
      when(reader.next()).thenReturn(event);

      // when
      final PollResult result = actor.poll(100L, 10, 0).join();

      // then
      assertThat(result.nextPosition()).isEqualTo(101L);
    }

    @Test
    void shouldReturnEmptyEventsWithTailPositionWhenNoRecordsAtFromPosition() {
      // given — fromPosition (999) is beyond the current log end (last written = 998):
      // seek() returns false AND hasNext() returns false → emptyResultWithTail() path.
      when(reader.seek(999L)).thenReturn(false);
      when(reader.hasNext()).thenReturn(false);
      when(reader.seekToEnd()).thenReturn(998L); // last written is 998

      // when
      final PollResult result = actor.poll(999L, 10, 0).join();

      // then — empty events; nextPosition = lastPos + 1
      assertThat(result.events()).isEmpty();
      assertThat(result.nextPosition()).isEqualTo(999L);
    }

    @Test
    void shouldResolveMinusOneToFirstAvailableEvent() {
      // given — fromPosition = -1 → seekToFirstEvent()
      final var event = mockEvent(500L, new byte[] {42});
      when(reader.hasNext()).thenReturn(true, false);
      when(reader.next()).thenReturn(event);

      // when
      final PollResult result = actor.poll(-1L, 10, 0).join();

      // then
      verify(reader).seekToFirstEvent();
      assertThat(result.events()).hasSize(1);
      assertThat(result.events().get(0).position()).isEqualTo(500L);
    }

    @Test
    void shouldReturnZeroNextPositionForEmptyLog() {
      // given — seek succeeds but no events; seekToEnd() returns -1 (empty log)
      when(reader.seek(1L)).thenReturn(true);
      when(reader.hasNext()).thenReturn(false);
      when(reader.seekToEnd()).thenReturn(-1L);

      // when
      final PollResult result = actor.poll(1L, 10, 0).join();

      // then
      assertThat(result.events()).isEmpty();
      assertThat(result.nextPosition()).isEqualTo(0L);
    }
  }

  // ---------------------------------------------------------------------------
  // Poll: truncated position detection

  @Nested
  class TruncatedPosition {

    @Test
    void shouldThrowPositionTruncatedExceptionWhenSeekFails() {
      // given — fromPosition = 100 doesn't exist; reader jumps to 500 (first available)
      when(reader.seek(100L)).thenReturn(false);
      when(reader.hasNext()).thenReturn(true);
      final var nextEvent = mockEvent(500L, new byte[] {1});
      when(reader.peekNext()).thenReturn(nextEvent);

      // when
      final var future = actor.poll(100L, 10, 0);

      // then
      assertThat(future).failsWithin(Duration.ofSeconds(5));
      final Throwable cause = future.toCompletableFuture().exceptionNow();
      assertThat(cause).isInstanceOf(PositionTruncatedException.class);
      assertThat(((PositionTruncatedException) cause).getOldestAvailablePosition()).isEqualTo(500L);
    }

    @Test
    void shouldNotThrowTruncationExceptionForMinusOneSentinel() {
      // given — fromPosition = -1 always resolves without truncation check
      when(reader.hasNext()).thenReturn(false);
      when(reader.seekToEnd()).thenReturn(-1L);

      // when / then — should complete normally (not throw)
      assertThat(actor.poll(-1L, 10, 0)).succeedsWithin(Duration.ofSeconds(5));
    }
  }

  // ---------------------------------------------------------------------------
  // Poll: invalid fromPosition

  @Nested
  class InvalidFromPosition {

    @Test
    void shouldThrowInvalidPositionExceptionForNegativeBeyondMinusOne() {
      // given
      final var future = actor.poll(-2L, 10, 0);

      // then
      assertThat(future).failsWithin(Duration.ofSeconds(5));
      assertThat(future.toCompletableFuture().exceptionNow())
          .isInstanceOf(InvalidPositionException.class);
    }

    @Test
    void shouldThrowInvalidPositionExceptionForLargeNegative() {
      final var future = actor.poll(Long.MIN_VALUE, 10, 0);

      assertThat(future).failsWithin(Duration.ofSeconds(5));
      assertThat(future.toCompletableFuture().exceptionNow())
          .isInstanceOf(InvalidPositionException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // Long-poll: park / wake

  @Nested
  class LongPoll {

    @Test
    void shouldCompleteWhenRecordAvailableListenerFires() throws Exception {
      // given — first read returns empty; next read returns one event
      when(reader.seek(anyLong())).thenReturn(true);
      // First call to hasNext (in poll()): false → park
      // Second call to hasNext (in wakeAllParked → doRead): true, false
      when(reader.hasNext()).thenReturn(false, true, false);
      final var wakeEvent = mockEvent(1L, new byte[] {99});
      when(reader.next()).thenReturn(wakeEvent);
      when(reader.seekToEnd()).thenReturn(0L);

      // Capture the LogRecordAwaiter registered on the LogStream
      final ArgumentCaptor<LogRecordAwaiter> awaiterCaptor =
          ArgumentCaptor.forClass(LogRecordAwaiter.class);

      // when — park with 5-second ceiling
      final var pollFuture = actor.poll(1L, 10, 5_000);

      // Verify the actor registered a listener
      verify(logStream, atLeastOnce()).registerRecordAvailableListener(awaiterCaptor.capture());

      // Simulate a new record being committed: call the captured listener
      awaiterCaptor.getValue().onRecordAvailable();

      // then
      final PollResult result = pollFuture.toCompletableFuture().get();
      assertThat(result.events()).hasSize(1);
      assertThat(result.events().get(0).payload()).containsExactly(99);
    }

    @Test
    void shouldReturnEmptyAfterTimeoutWhenNoRecordsArrive() {
      // given — no records ever arrive
      when(reader.seek(anyLong())).thenReturn(true);
      when(reader.hasNext()).thenReturn(false);
      when(reader.seekToEnd()).thenReturn(-1L);

      // when — park with a very short ceiling (50 ms)
      final var pollFuture = actor.poll(1L, 10, 50);

      // then — future completes (possibly empty) after the ceiling
      assertThat(pollFuture).succeedsWithin(Duration.ofSeconds(5));
      assertThat(pollFuture.join().events()).isEmpty();
    }

    @Test
    void shouldClampServerWaitMsToConfiguredCeiling() {
      // given — configured ceiling is 30 000 ms; request 60 000 ms (above ceiling)
      when(reader.seek(anyLong())).thenReturn(true);
      when(reader.hasNext()).thenReturn(false);
      when(reader.seekToEnd()).thenReturn(0L);

      // when — the actor must silently clamp and still eventually complete
      final var pollFuture = actor.poll(1L, 10, 60_000);

      // Simulate immediate record notification so we don't actually wait 30 s
      final ArgumentCaptor<LogRecordAwaiter> awaiterCaptor =
          ArgumentCaptor.forClass(LogRecordAwaiter.class);
      verify(logStream, atLeastOnce()).registerRecordAvailableListener(awaiterCaptor.capture());
      awaiterCaptor.getValue().onRecordAvailable();

      // then — future must complete after the simulated notification
      assertThat(pollFuture).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void shouldWakeMultiplePendingPolls() throws Exception {
      // given — every read returns empty, then one event
      when(reader.seek(anyLong())).thenReturn(true);
      when(reader.hasNext()).thenReturn(false, false, true, false, true, false);
      final var wakeEvent1 = mockEvent(1L, new byte[] {1});
      final var wakeEvent2 = mockEvent(2L, new byte[] {2});
      when(reader.next()).thenReturn(wakeEvent1, wakeEvent2);
      when(reader.seekToEnd()).thenReturn(-1L);

      // Park two polls
      final var future1 = actor.poll(1L, 5, 5_000);
      final var future2 = actor.poll(2L, 5, 5_000);

      // Capture the most recently registered awaiter and fire it
      final ArgumentCaptor<LogRecordAwaiter> awaiterCaptor =
          ArgumentCaptor.forClass(LogRecordAwaiter.class);
      verify(logStream, atLeastOnce()).registerRecordAvailableListener(awaiterCaptor.capture());
      awaiterCaptor.getValue().onRecordAvailable();

      // then — both futures must complete
      assertThat(future1).succeedsWithin(Duration.ofSeconds(5));
      assertThat(future2).succeedsWithin(Duration.ofSeconds(5));
    }
  }

  // ---------------------------------------------------------------------------
  // getLatestPosition

  @Nested
  class GetLatestPosition {

    @Test
    void shouldReturnTailPosition() {
      // given — seekToEnd() returns 42 (last written position); tail = 43 (next write position)
      when(reader.seekToEnd()).thenReturn(42L);

      // when
      final long pos = actor.getLatestPosition().join();

      // then — getLatestPosition() returns lastPos + 1 (the position the next event would occupy)
      assertThat(pos).isEqualTo(43L);
    }

    @Test
    void shouldReturnZeroForEmptyLog() {
      // given — seekToEnd returns -1 (empty log)
      when(reader.seekToEnd()).thenReturn(-1L);

      // when / then
      assertThat(actor.getLatestPosition().join()).isEqualTo(0L);
    }

    @Test
    void shouldCompleteExceptionallyWhenDisconnected() {
      // given
      actor.disconnect().join();

      // when / then
      assertThat(actor.getLatestPosition()).failsWithin(Duration.ofSeconds(5));
    }
  }

  // ---------------------------------------------------------------------------
  // Connect / disconnect lifecycle

  @Nested
  class ConnectDisconnect {

    @Test
    void shouldRejectPollWhenDisconnected() {
      // given
      actor.disconnect().join();

      // when / then
      assertThat(actor.poll(1L, 10, 0)).failsWithin(Duration.ofSeconds(5));
    }

    @Test
    void shouldUnregisterListenerOnDisconnect() {
      // when
      actor.disconnect().join();

      // then — the listener was unregistered from the LogStream
      verify(logStream).removeRecordAvailableListener(any());
    }

    @Test
    void shouldClosePreviousConnectionOnReconnect() {
      // given — already connected (setUp called connect); add a new LogStream
      final var newReader = mock(LogStreamReader.class);
      final var newLogStream = mock(LogStream.class);
      when(newLogStream.newLogStreamReader()).thenReturn(newReader);
      when(newReader.seek(anyLong())).thenReturn(true);
      when(newReader.hasNext()).thenReturn(false);
      when(newReader.seekToEnd()).thenReturn(-1L);

      // when — reconnect (replaces old LogStream)
      actor.connect(newLogStream).join();

      // then — old LogStream had its listener removed
      verify(logStream).removeRecordAvailableListener(any());
      // And the new LogStream had a listener registered
      verify(newLogStream).registerRecordAvailableListener(any());
    }

    @Test
    void shouldFailParkedPollsOnDisconnect() {
      // given — park a long-poll with a long ceiling so it won't time out
      when(reader.seek(anyLong())).thenReturn(true);
      when(reader.hasNext()).thenReturn(false);
      when(reader.seekToEnd()).thenReturn(-1L);

      final var parkedFuture = actor.poll(1L, 10, 30_000);

      // when — disconnect while poll is parked
      actor.disconnect().join();

      // then — parked future completes exceptionally
      assertThat(parkedFuture).failsWithin(Duration.ofSeconds(5));
      assertThat(parkedFuture.toCompletableFuture().exceptionNow())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("lost leadership");
    }

    @Test
    void shouldAcceptPollAfterReconnect() {
      // given
      actor.disconnect().join();

      final var newReader = mock(LogStreamReader.class);
      final var newLogStream = mock(LogStream.class);
      when(newLogStream.newLogStreamReader()).thenReturn(newReader);
      when(newReader.seek(1L)).thenReturn(true);
      when(newReader.hasNext()).thenReturn(true, false);
      final var reconnectEvent = mockEvent(1L, new byte[] {55});
      when(newReader.next()).thenReturn(reconnectEvent);

      actor.connect(newLogStream).join();

      // when
      final PollResult result = actor.poll(1L, 10, 0).join();

      // then
      assertThat(result.events()).hasSize(1);
      assertThat(result.events().get(0).payload()).containsExactly(55);
    }
  }
}
