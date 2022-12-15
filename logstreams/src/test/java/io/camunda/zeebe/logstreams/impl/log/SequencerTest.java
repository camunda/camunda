/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.lang.Thread.State;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@SuppressWarnings("resource")
@Execution(ExecutionMode.CONCURRENT)
final class SequencerTest {

  @Test
  void canReadAfterSingleWrite() {
    // given
    final var logStorage = new TestLogStorage();
    final var sequencer = new Sequencer(1, 1, 16, logStorage);
    final var entry = TestEntry.ofDefaults();

    // when
    sequencer.tryWrite(entry);

    // then
    Assertions.assertThat(logStorage.batches())
        .singleElement()
        .extracting(SequencedBatch::entries)
        .asList()
        .containsExactly(entry);
  }

  @Test
  void canReadAfterBatchWrite() {
    // given
    final var logStorage = new TestLogStorage();
    final var sequencer = new Sequencer(1, 1, 16, logStorage);
    final var entries =
        List.of(TestEntry.ofDefaults(), TestEntry.ofDefaults(), TestEntry.ofDefaults());

    // when
    sequencer.tryWrite(entries);

    // then
    Assertions.assertThat(logStorage.batches())
        .singleElement()
        .extracting(SequencedBatch::entries)
        .asList()
        .containsExactlyElementsOf(entries);
  }

  @Test
  void writingSingleEntryIncreasesPositions() {
    // given
    final var logStorage = new TestLogStorage();
    final var initialPosition = 1;
    final var sequencer = new Sequencer(1, initialPosition, 16 * 1024 * 1024, logStorage);

    // when
    final var firstWrite = sequencer.tryWrite(TestEntry.ofDefaults());
    final var secondWrite = sequencer.tryWrite(TestEntry.ofDefaults());

    // then
    Assertions.assertThat(firstWrite).isPositive().isEqualTo(initialPosition);
    Assertions.assertThat(secondWrite).isPositive().isEqualTo(firstWrite + 1);
  }

  @Test
  void writingMultipleEntriesIncreasesPositions() {
    // given
    final var logStorage = new TestLogStorage();
    final var initialPosition = 1;
    final var sequencer = new Sequencer(1, initialPosition, 16 * 1024 * 1024, logStorage);
    final var entries =
        List.of(TestEntry.ofDefaults(), TestEntry.ofDefaults(), TestEntry.ofDefaults());
    // when
    final var result = sequencer.tryWrite(entries);

    // then
    Assertions.assertThat(result).isPositive().isEqualTo(initialPosition + entries.size() - 1);
  }

  @Test
  void keepsPositionsWithSingleWriter() throws InterruptedException {
    // given
    final var logStorage = new TestLogStorage();
    final var initialPosition = 1L;
    final var entriesToWrite = 10_000L;
    final var sequencer = new Sequencer(1, initialPosition, 16 * 1024 * 1024, logStorage);
    final var batch = List.of(TestEntry.ofKey(1));
    final var writer = newWriterThread(sequencer, initialPosition, entriesToWrite, batch, true);

    // when
    writer.start();
    Awaitility.await("writer thread finishes writing all entries")
        .until(writer::getState, state -> state == State.TERMINATED);

    // then -- readers and writers don't throw
    Assertions.assertThat(logStorage.batches).hasSize((int) entriesToWrite);
    var position = initialPosition;
    for (final var readBatch : logStorage.batches) {
      Assertions.assertThat(readBatch.firstPosition()).isEqualTo(position);
      position += readBatch.entries().size();
    }
  }

  @Test
  void keepsPositionsWithMultipleWriters() throws InterruptedException {
    // given
    final var logStorage = new TestLogStorage();
    final var writers = 3;

    final var initialPosition = 1L;
    final var entriesToWrite = 10_000L;
    final var entriesToRead = writers * entriesToWrite;
    final var sequencer = new Sequencer(1, initialPosition, 16 * 1024 * 1024, logStorage);
    final var batch = List.of(TestEntry.ofKey(1));
    final var writerThreads =
        IntStream.range(0, writers)
            .mapToObj(
                i -> newWriterThread(sequencer, initialPosition, entriesToWrite, batch, false))
            .toList();

    // when
    writerThreads.forEach(Thread::start);
    writerThreads.forEach(
        thread -> {
          try {
            thread.join(10 * 1000);
          } catch (final InterruptedException e) {
            throw new RuntimeException(e);
          }
        });

    // then
    Assertions.assertThat(logStorage.batches).hasSize((int) entriesToRead);
    var position = initialPosition;
    for (final var readBatch : logStorage.batches) {
      Assertions.assertThat(readBatch.firstPosition()).isEqualTo(position);
      position += readBatch.entries().size();
    }
  }

  @Test
  void keepsPositionsWithMultipleWritersWritingMultipleEntries() throws InterruptedException {
    // given
    final var logStorage = new TestLogStorage();
    final var writers = 3;

    final var initialPosition = 1L;
    final var batchesToWrite = 10_000L;
    final var batchesToRead = writers * batchesToWrite;
    final var sequencer = new Sequencer(1, initialPosition, 16 * 1024 * 1024, logStorage);
    final var batch =
        List.of(TestEntry.ofKey(1), TestEntry.ofKey(1), TestEntry.ofKey(1), TestEntry.ofKey(1));
    final var writerThreads =
        IntStream.range(0, writers)
            .mapToObj(
                i -> newWriterThread(sequencer, initialPosition, batchesToWrite, batch, false))
            .toList();

    // when
    writerThreads.forEach(Thread::start);
    writerThreads.forEach(
        thread -> {
          try {
            thread.join(10 * 1000);
          } catch (final InterruptedException e) {
            throw new RuntimeException(e);
          }
        });

    // then
    Assertions.assertThat(logStorage.batches).hasSize((int) batchesToRead);
    var position = initialPosition;
    for (final var readBatch : logStorage.batches) {
      Assertions.assertThat(readBatch.firstPosition()).isEqualTo(position);
      position += readBatch.entries().size();
    }
  }

  private Thread newWriterThread(
      final Sequencer sequencer,
      final long initialPosition,
      final long batchesToWrite,
      final List<LogAppendEntry> batchToWrite,
      final boolean isOnlyWriter) {
    return new Thread(
        () -> {
          var batchesWritten = 0L;
          var lastWrittenPosition = initialPosition - 1;
          while (batchesWritten < batchesToWrite) {
            final var result = sequencer.tryWrite(batchToWrite);
            if (result > 0) {
              if (isOnlyWriter) {
                Assertions.assertThat(result).isEqualTo(lastWrittenPosition + batchToWrite.size());
              } else {
                Assertions.assertThat(result).isGreaterThan(lastWrittenPosition);
              }
              lastWrittenPosition = result;
              batchesWritten += 1;
            } else {
              LockSupport.parkNanos(1_000_000);
            }
          }
        });
  }

  private static final class TestLogStorage implements LogStorage {
    final List<SequencedBatch> batches = new LinkedList<>();

    public List<SequencedBatch> batches() {
      return batches;
    }

    @Override
    public LogStorageReader newReader() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void append(
        final long lowestPosition,
        final long highestPosition,
        final BufferWriter bufferWriter,
        final AppendListener listener) {
      batches.add((SequencedBatch) bufferWriter);
      listener.onCommit(1);
      listener.onWrite(1);
    }

    @Override
    public void append(
        final long lowestPosition,
        final long highestPosition,
        final ByteBuffer blockBuffer,
        final AppendListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addCommitListener(final CommitListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeCommitListener(final CommitListener listener) {
      throw new UnsupportedOperationException();
    }
  }
}
