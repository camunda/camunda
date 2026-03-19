/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.ByteBuffer;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link EventBridgeLogStorage}, verifying that the RAFT→LogStream bridge correctly
 * forwards append, commit, and read operations.
 */
final class EventBridgeLogStorageTest {

  private RaftLog raftLog;
  private EventBridgeLogStorage logStorage;
  private EventBridgeLogStorage.EventBridgeLogStorageReader reader;

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void setUp(@TempDir final File tempDir) {
    raftLog =
        RaftLog.builder(meterRegistry)
            .withDirectory(tempDir)
            .withMetaStore(mock(JournalMetaStore.class))
            .build();
    final SynchronousAppender appender = new SynchronousAppender();
    logStorage = new EventBridgeLogStorage(raftLog::openUncommittedReader, appender);
    reader = (EventBridgeLogStorage.EventBridgeLogStorageReader) logStorage.newReader();
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(raftLog, reader);
  }

  // ── Reader tests ──────────────────────────────────────────────────────────

  @Nested
  class ReaderTest {

    @Test
    void shouldReadFirstBlockWhenInitialized() {
      // given
      appendBlock(1);
      appendBlock(2);

      // when / then
      assertThat(reader).hasNext();
      assertThat(reader.next()).isEqualTo(intToBuffer(1));
    }

    @Test
    void shouldIterateAllBlocks() {
      // given
      appendBlock(1);
      appendBlock(2);

      // when / then – assert each block immediately; the buffer is reused across calls
      assertThat(reader).hasNext();
      assertThat(reader.next()).isEqualTo(intToBuffer(1));
      assertThat(reader).hasNext();
      assertThat(reader.next()).isEqualTo(intToBuffer(2));
      assertThat(reader.hasNext()).isFalse();
    }

    @Test
    void shouldNotHaveNextWhenLogIsEmpty() {
      // given an empty log
      // then
      assertThat(reader.hasNext()).isFalse();
    }

    @Test
    void shouldDetectNewBlockAfterAppend() {
      // given an empty log at reader creation time
      // when a block is appended afterwards
      appendBlock(42);

      // then the reader (which had empty look-ahead) still finds it
      assertThat(reader).hasNext();
      assertThat(reader.next()).isEqualTo(intToBuffer(42));
    }

    @Test
    void shouldSeekToFirstBlock() {
      // given
      appendBlock(1);
      appendBlock(2);

      // when
      reader.seek(Long.MIN_VALUE);

      // then
      assertThat(reader).hasNext();
      assertThat(reader.next()).isEqualTo(intToBuffer(1));
    }

    @Test
    void shouldSeekToLastBlock() {
      // given
      appendBlock(1);
      appendBlock(2);

      // when
      reader.seek(Long.MAX_VALUE);

      // then
      assertThat(reader).hasNext();
      assertThat(reader.next()).isEqualTo(intToBuffer(2));
    }

    @Test
    void shouldSeekToBlockContainingPosition() {
      // given blocks with position ranges [1,1], [2,4], [5,5]
      appendBlockWithRange(1, 1, 1);
      appendBlockWithRange(2, 4, 99);
      appendBlockWithRange(5, 5, 5);

      // when seeking to position 3 (inside the second block)
      reader.seek(3);

      // then the second block is returned
      assertThat(reader).hasNext();
      assertThat(reader.next()).isEqualTo(intToBuffer(99));
    }

    @Test
    void shouldSeekToBlockWithHighestPositionBelowGivenOne() {
      // given blocks with positions 1 and 3
      appendBlock(1);
      appendBlock(3);

      // when seeking to position 2 (no exact match; block at position 1 is the correct entry)
      reader.seek(2);

      // then the block at position 1 is returned
      assertThat(reader).hasNext();
      assertThat(reader.next()).isEqualTo(intToBuffer(1));
    }

    @Test
    void shouldNotHaveNextAfterLastBlockConsumed() {
      // given
      appendBlock(1);
      appendBlock(2);

      // when all blocks are consumed
      reader.seek(2);
      reader.next();

      // then
      assertThat(reader.hasNext()).isFalse();
    }
  }

  // ── Commit-listener tests ─────────────────────────────────────────────────

  @Nested
  class CommitListenerTest {

    @Test
    void shouldNotifyRegisteredCommitListenerOnRaftCommit() {
      // given
      final CommitListener listener = mock(CommitListener.class);
      logStorage.addCommitListener(listener);

      // when the RAFT layer fires onCommit
      logStorage.onCommit(1L);

      // then the LogStorage CommitListener is notified
      verify(listener).onCommit();
    }

    @Test
    void shouldNotNotifyRemovedCommitListener() {
      // given
      final CommitListener listener = mock(CommitListener.class);
      logStorage.addCommitListener(listener);
      logStorage.removeCommitListener(listener);

      // when
      logStorage.onCommit(1L);

      // then
      verifyNoInteractions(listener);
    }

    @Test
    void shouldNotifyMultipleCommitListeners() {
      // given
      final CommitListener first = mock(CommitListener.class);
      final CommitListener second = mock(CommitListener.class);
      logStorage.addCommitListener(first);
      logStorage.addCommitListener(second);

      // when
      logStorage.onCommit(1L);

      // then
      verify(first).onCommit();
      verify(second).onCommit();
    }
  }

  // ── AppendListener bridge tests ───────────────────────────────────────────

  @Nested
  class AppendListenerBridgeTest {

    @Test
    void shouldForwardOnWriteToLogStorageListener() {
      // given
      final AppendListener listener = mock(AppendListener.class);

      // when a block is appended (the SynchronousAppender drives onWrite and onCommit)
      logStorage.append(1L, 1L, intToBuffer(7).byteBuffer(), listener);

      // then onWrite was called with the RAFT index and the highest position
      verify(listener).onWrite(1L, 1L);
    }

    @Test
    void shouldForwardOnCommitToLogStorageListener() {
      // given
      final AppendListener listener = mock(AppendListener.class);

      // when
      logStorage.append(1L, 1L, intToBuffer(7).byteBuffer(), listener);

      // then onCommit was called with the RAFT index and the highest position
      verify(listener).onCommit(1L, 1L);
    }
  }

  // ── Helper methods ────────────────────────────────────────────────────────

  private void appendBlock(final int positionAndValue) {
    appendBlockWithRange(positionAndValue, positionAndValue, positionAndValue);
  }

  private void appendBlockWithRange(
      final long lowestPosition, final long highestPosition, final int value) {
    logStorage.append(
        lowestPosition,
        highestPosition,
        intToBuffer(value).byteBuffer(),
        new LogStorage.AppendListener() {});
  }

  private static DirectBuffer intToBuffer(final int value) {
    return new UnsafeBuffer(ByteBuffer.allocateDirect(Integer.BYTES).putInt(0, value));
  }

  /**
   * A synchronous {@link ZeebeLogAppender} that writes the entry to the real {@link RaftLog} and
   * immediately drives {@code onWrite} and {@code onCommit} callbacks — so tests do not need an
   * actual RAFT cluster.
   */
  private final class SynchronousAppender implements ZeebeLogAppender {

    @Override
    public void appendEntry(final ApplicationEntry entry, final AppendListener appendListener) {
      final var indexed = raftLog.append(new RaftLogEntry(1, entry));
      appendListener.onWrite(indexed);
      raftLog.setCommitIndex(indexed.index());
      appendListener.onCommit(indexed.index(), entry.highestPosition());
    }
  }
}
