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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.core.logappend.RawEventRecordValue;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PublishActor}.
 *
 * <p>Covers the publish-batch sequencing path, long-poll await/notify, and the connect/disconnect
 * lifecycle that wires (or unwires) the actor from its {@link LogStreamWriter} and {@link
 * LogStreamReader}.
 */
class PublishActorTest {

  private static final int PARTITION_ID = 0;
  private static final EventBridgeProperties PROPERTIES =
      new EventBridgeProperties(null, null, null, null, null, null, null);

  private ActorScheduler scheduler;
  private LogStreamWriter writer;
  private LogStreamReader reader;
  private PublishActor actor;

  @BeforeEach
  void setUp() {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("test-scheduler")
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();

    writer = mock(LogStreamWriter.class);
    reader = mock(LogStreamReader.class);

    actor = new PublishActor(PARTITION_ID, writer, reader, PROPERTIES);
    scheduler.submitActor(actor).join();
  }

  @AfterEach
  void tearDown() throws Exception {
    actor.closeAsync().join();
    scheduler.close();
  }

  // ---------------------------------------------------------------------------
  // publishBatch

  @Nested
  class PublishBatch {

    @Test
    void shouldReturnSinglePositionForSingleEvent() {
      // given
      when(writer.tryWrite(any(), anyList(), anyLong())).thenReturn(Either.right(1001L));

      // when
      final var positions = actor.publishBatch(List.of(new byte[] {1, 2, 3})).join();

      // then
      assertThat(positions).containsExactly(1001L);
    }

    @Test
    void shouldReturnPositionsInOrderForBatch() {
      // given — highest position is 1003; batch of 3 → [1001, 1002, 1003]
      when(writer.tryWrite(any(), anyList(), anyLong())).thenReturn(Either.right(1003L));

      // when
      final var positions =
          actor.publishBatch(List.of(new byte[] {1}, new byte[] {2}, new byte[] {3})).join();

      // then
      assertThat(positions).containsExactly(1001L, 1002L, 1003L);
    }

    @Test
    void shouldReturnFirstPositionEqualToHighestMinusBatchSizePlusOne() {
      // given — batch of 5; highest = 20
      when(writer.tryWrite(any(), anyList(), anyLong())).thenReturn(Either.right(20L));

      // when
      final var positions =
          actor
              .publishBatch(
                  List.of(new byte[1], new byte[1], new byte[1], new byte[1], new byte[1]))
              .join();

      // then — positions should be [16, 17, 18, 19, 20]
      assertThat(positions).hasSize(5);
      assertThat(positions.get(0)).isEqualTo(16L);
      assertThat(positions.get(4)).isEqualTo(20L);
    }

    @Test
    void shouldCompleteExceptionallyWhenWriterFails() throws Exception {
      // given
      when(writer.tryWrite(any(), anyList(), anyLong()))
          .thenReturn(Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED));

      // when
      final var future = actor.publishBatch(List.of(new byte[] {0}));

      // then
      assertThat(future).failsWithin(java.time.Duration.ofSeconds(5));
      assertThat(future.toCompletableFuture().exceptionNow())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Log write failed on partition");
    }

    @Test
    void shouldCompleteExceptionallyWhenDisconnected() {
      // given — disconnect before publishing
      actor.disconnect().join();

      // when
      final var future = actor.publishBatch(List.of(new byte[] {0}));

      // then
      assertThat(future).failsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    void shouldNotWriteToLogStreamWhenDisconnected() {
      // given
      actor.disconnect().join();

      // when
      try {
        actor.publishBatch(List.of(new byte[] {0})).join();
      } catch (final Exception ignored) {
        // expected — future completes exceptionally when disconnected
      }

      // then — writer must not be called when disconnected
      verify(writer, never()).tryWrite(any(), anyList(), anyLong());
    }

    @Test
    void shouldReconnectAfterDisconnectAndAcceptWrites() {
      // given — disconnect and then reconnect with fresh mocks
      actor.disconnect().join();

      final var newWriter = mock(LogStreamWriter.class);
      final var newReader = mock(LogStreamReader.class);
      when(newWriter.tryWrite(any(), anyList(), anyLong())).thenReturn(Either.right(5L));
      actor.connect(newWriter, newReader).join();

      // when
      final var positions = actor.publishBatch(List.of(new byte[] {1})).join();

      // then
      assertThat(positions).containsExactly(5L);
    }
  }

  // ---------------------------------------------------------------------------
  // getLatestPosition

  @Nested
  class GetLatestPosition {

    @Test
    void shouldReturnPositionFromReader() {
      // given
      when(reader.seekToEnd()).thenReturn(42L);

      // when
      final long pos = actor.getLatestPosition().join();

      // then
      assertThat(pos).isEqualTo(42L);
    }

    @Test
    void shouldReturnZeroForEmptyLog() {
      // given — seekToEnd returns -1 (empty log sentinel)
      when(reader.seekToEnd()).thenReturn(-1L);

      // when
      final long pos = actor.getLatestPosition().join();

      // then — negative values are clamped to 0
      assertThat(pos).isEqualTo(0L);
    }

    @Test
    void shouldReturnZeroForVeryNegativePosition() {
      // given
      when(reader.seekToEnd()).thenReturn(Long.MIN_VALUE);

      // when / then
      assertThat(actor.getLatestPosition().join()).isEqualTo(0L);
    }

    @Test
    void shouldCompleteExceptionallyWhenDisconnected() {
      // given
      actor.disconnect().join();

      // when / then
      assertThat(actor.getLatestPosition()).failsWithin(java.time.Duration.ofSeconds(5));
    }
  }

  // ---------------------------------------------------------------------------
  // awaitRecords (long-poll)

  @Nested
  class AwaitRecords {

    @Test
    void shouldCompleteWhenPublishBatchIsCalledBeforeTimeout() {
      // given
      when(writer.tryWrite(any(), anyList(), anyLong())).thenReturn(Either.right(1L));

      // Park a long-poll with a 5-second ceiling so it won't time out during the test.
      final var awaitFuture = actor.awaitRecords(0, 5_000);

      // when — write one event; notifyAwaiters is called inside publishBatch
      actor.publishBatch(List.of(new byte[] {42})).join();

      // then — the parked future should complete (woken by notify)
      assertThat(awaitFuture).succeedsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    void shouldCompleteAfterTimeoutWhenNoRecordsArriveTest() {
      // when — park with a short ceiling (50 ms) and do NOT write anything
      final var awaitFuture = actor.awaitRecords(0, 50);

      // then — future must complete (with null) after the ceiling elapses
      assertThat(awaitFuture).succeedsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    void shouldClampWaitMsToConfiguredCeiling() {
      // given — configured ceiling is 30_000 ms (default); request 60_000 ms
      // The actor should clamp to 30_000 and not blow up.
      // We verify indirectly: the future must still eventually complete.
      final var awaitFuture = actor.awaitRecords(0, 60_000);

      // Force completion by writing a batch immediately.
      when(writer.tryWrite(any(), anyList(), anyLong())).thenReturn(Either.right(1L));
      actor.publishBatch(List.of(new byte[] {1})).join();

      assertThat(awaitFuture).succeedsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    void shouldCompleteExceptionallyWhenDisconnected() {
      // given
      actor.disconnect().join();

      // when / then
      assertThat(actor.awaitRecords(0, 1_000)).failsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    void shouldWakeMultiplePendingAwaiters() {
      // given — register two long-poll futures
      when(writer.tryWrite(any(), anyList(), anyLong())).thenReturn(Either.right(1L));

      final var future1 = actor.awaitRecords(0, 5_000);
      final var future2 = actor.awaitRecords(0, 5_000);

      // when — single write notifies all awaiters
      actor.publishBatch(List.of(new byte[] {1})).join();

      // then
      assertThat(future1).succeedsWithin(java.time.Duration.ofSeconds(5));
      assertThat(future2).succeedsWithin(java.time.Duration.ofSeconds(5));
    }
  }

  // ---------------------------------------------------------------------------
  // connect / disconnect lifecycle

  @Nested
  class ConnectDisconnect {

    @Test
    void shouldRejectPublishAfterDisconnect() {
      // given
      actor.disconnect().join();

      // when / then
      assertThat(actor.publishBatch(List.of(new byte[] {0})))
          .failsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    void shouldAcceptPublishAfterReconnect() {
      // given
      actor.disconnect().join();

      final var newWriter = mock(LogStreamWriter.class);
      final var newReader = mock(LogStreamReader.class);
      when(newWriter.tryWrite(any(), anyList(), anyLong())).thenReturn(Either.right(99L));
      actor.connect(newWriter, newReader).join();

      // when
      final var positions = actor.publishBatch(List.of(new byte[] {7})).join();

      // then
      assertThat(positions).containsExactly(99L);
    }

    @Test
    void shouldRejectGetLatestPositionAfterDisconnect() {
      // given
      actor.disconnect().join();

      // when / then
      assertThat(actor.getLatestPosition()).failsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    void shouldAcceptGetLatestPositionAfterReconnect() {
      // given
      actor.disconnect().join();
      final var newWriter = mock(LogStreamWriter.class);
      final var newReader = mock(LogStreamReader.class);
      when(newReader.seekToEnd()).thenReturn(200L);
      actor.connect(newWriter, newReader).join();

      // when / then
      assertThat(actor.getLatestPosition().join()).isEqualTo(200L);
    }
  }

  // ---------------------------------------------------------------------------
  // pollRecords

  @Nested
  class PollRecords {

    @Test
    void shouldReturnEmptyListWhenLogIsEmpty() {
      // given — no records available
      when(reader.seek(0L)).thenReturn(false);
      when(reader.hasNext()).thenReturn(false);
      when(reader.seekToEnd()).thenReturn(-1L);

      // when
      final var result = actor.pollRecords(0L, 10).join();

      // then
      assertThat(result).isInstanceOf(PublishActor.PollRecordsResult.Success.class);
      final var success = (PublishActor.PollRecordsResult.Success) result;
      assertThat(success.records()).isEmpty();
      assertThat(success.nextPosition()).isEqualTo(0L); // max(0, -1) = 0
    }

    @Test
    void shouldReturnRecordsFromLog() {
      // given — two consecutive records at positions 1 and 2
      final var event1 = mockLoggedEvent(1L, new byte[] {10, 20});
      final var event2 = mockLoggedEvent(2L, new byte[] {30, 40});
      when(reader.seek(1L)).thenReturn(true);
      when(reader.hasNext()).thenReturn(true, true, false);
      when(reader.next()).thenReturn(event1, event2);

      // when
      final var result = actor.pollRecords(1L, 10).join();

      // then
      assertThat(result).isInstanceOf(PublishActor.PollRecordsResult.Success.class);
      final var success = (PublishActor.PollRecordsResult.Success) result;
      assertThat(success.records()).hasSize(2);
      assertThat(success.records().get(0).position()).isEqualTo(1L);
      assertThat(success.records().get(0).payload()).containsExactly(10, 20);
      assertThat(success.records().get(1).position()).isEqualTo(2L);
      assertThat(success.nextPosition()).isEqualTo(3L); // last position + 1
    }

    @Test
    void shouldRespectMaxRecordsLimit() {
      // given — three records but maxRecords = 2
      final var event1 = mockLoggedEvent(1L, new byte[] {1});
      final var event2 = mockLoggedEvent(2L, new byte[] {2});
      when(reader.seek(1L)).thenReturn(true);
      when(reader.hasNext()).thenReturn(true, true, true);
      when(reader.next()).thenReturn(event1, event2);

      // when
      final var result = actor.pollRecords(1L, 2).join();

      // then
      assertThat(result).isInstanceOf(PublishActor.PollRecordsResult.Success.class);
      assertThat(((PublishActor.PollRecordsResult.Success) result).records()).hasSize(2);
    }

    @Test
    void shouldSeekToFirstEventWhenFromPositionIsNegative() {
      // given — fromPosition = -1 means start from beginning
      when(reader.hasNext()).thenReturn(false);
      when(reader.seekToEnd()).thenReturn(-1L);

      // when
      actor.pollRecords(-1L, 10).join();

      // then — seekToFirstEvent was called, not seek(position)
      verify(reader).seekToFirstEvent();
      verify(reader, never()).seek(anyLong());
    }

    @Test
    void shouldReturnPositionTruncatedWhenRequestedPositionNoLongerExists() {
      // given — seek returns false (not found), but there are newer entries
      final var newerEvent = mockLoggedEvent(500L, new byte[] {0});
      when(reader.seek(100L)).thenReturn(false);
      when(reader.hasNext()).thenReturn(true);
      when(reader.peekNext()).thenReturn(newerEvent);

      // when
      final var result = actor.pollRecords(100L, 10).join();

      // then
      assertThat(result).isInstanceOf(PublishActor.PollRecordsResult.PositionTruncated.class);
      final var truncated = (PublishActor.PollRecordsResult.PositionTruncated) result;
      assertThat(truncated.oldestAvailablePosition()).isEqualTo(500L);
    }

    @Test
    void shouldCompleteExceptionallyWhenDisconnected() {
      // given
      actor.disconnect().join();

      // when / then
      assertThat(actor.pollRecords(0L, 10)).failsWithin(java.time.Duration.ofSeconds(5));
    }

    /**
     * Creates a minimal mock of {@link LoggedEvent} that returns the given position and populates a
     * {@link RawEventRecordValue} with the given payload when {@code readValue} is called.
     */
    private LoggedEvent mockLoggedEvent(final long position, final byte[] payload) {
      final var event = mock(LoggedEvent.class);
      when(event.getPosition()).thenReturn(position);
      org.mockito.Mockito.doAnswer(
              invocation -> {
                final var val = (RawEventRecordValue) invocation.getArgument(0);
                val.wrapPayload(payload);
                return null;
              })
          .when(event)
          .readValue(any(RawEventRecordValue.class));
      return event;
    }
  }
}
