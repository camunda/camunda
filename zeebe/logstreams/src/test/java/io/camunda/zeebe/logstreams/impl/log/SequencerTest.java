/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;

@SuppressWarnings("resource")
@Execution(ExecutionMode.CONCURRENT)
final class SequencerTest {

  @Test
  void writingSingleEntryIncreasesPositions() {
    // given
    final long initialPosition = 1L;
    final var logStorage = Mockito.mock(LogStorage.class);
    final var logStreamMetrics = new LogStreamMetrics(new SimpleMeterRegistry());
    final var sequencer =
        new Sequencer(
            logStorage,
            initialPosition,
            16,
            InstantSource.system(),
            new SequencerMetrics(new SimpleMeterRegistry()),
            new FlowControl(logStreamMetrics));

    // when
    final var result = sequencer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults());

    // then
    EitherAssert.assertThat(result).isRight().right().isEqualTo(initialPosition);
  }

  @Test
  void writingMultipleEntriesIncreasesPositions() {
    // given
    final long initialPosition = 1L;
    final var logStorage = Mockito.mock(LogStorage.class);
    final var logStreamMetrics = new LogStreamMetrics(new SimpleMeterRegistry());
    final var sequencer =
        new Sequencer(
            logStorage,
            initialPosition,
            16,
            InstantSource.system(),
            new SequencerMetrics(new SimpleMeterRegistry()),
            new FlowControl(logStreamMetrics));
    final var entries =
        List.of(TestEntry.ofDefaults(), TestEntry.ofDefaults(), TestEntry.ofDefaults());
    // when
    final var result = sequencer.tryWrite(WriteContext.internal(), entries);

    // then
    EitherAssert.assertThat(result)
        .isRight()
        .right()
        .isEqualTo(initialPosition + entries.size() - 1);
  }

  @Test
  void writesSingleEntryToLogStorage() {
    // given
    final var logStorage = Mockito.mock(LogStorage.class);
    final var logStreamMetrics = new LogStreamMetrics(new SimpleMeterRegistry());
    final var sequencer =
        new Sequencer(
            logStorage,
            1,
            16,
            InstantSource.system(),
            new SequencerMetrics(new SimpleMeterRegistry()),
            new FlowControl(logStreamMetrics));
    final var entry = TestEntry.ofDefaults();

    // when
    sequencer.tryWrite(WriteContext.internal(), entry);

    // then
    Mockito.verify(logStorage).append(eq(1L), eq(1L), any(BufferWriter.class), any());
  }

  @Test
  void writesMultipleEntriesToLogStorage() {
    // given
    final var logStorage = Mockito.mock(LogStorage.class);
    final var logStreamMetrics = new LogStreamMetrics(new SimpleMeterRegistry());
    final var sequencer =
        new Sequencer(
            logStorage,
            1,
            16,
            InstantSource.system(),
            new SequencerMetrics(new SimpleMeterRegistry()),
            new FlowControl(logStreamMetrics));
    final var entries =
        List.of(TestEntry.ofDefaults(), TestEntry.ofDefaults(), TestEntry.ofDefaults());

    // when
    sequencer.tryWrite(WriteContext.internal(), entries);

    // then
    Mockito.verify(logStorage).append(eq(1L), eq(3L), any(BufferWriter.class), any());
  }

  @Test
  void maintainsPositionWithSingleWriterAndSingleEntry() throws InterruptedException {
    // given
    final var logStorage = new VerifyingLogStorage();
    final var logStreamMetrics = new LogStreamMetrics(new SimpleMeterRegistry());
    final var sequencer =
        new Sequencer(
            logStorage,
            1,
            16,
            InstantSource.system(),
            new SequencerMetrics(new SimpleMeterRegistry()),
            new FlowControl(logStreamMetrics));
    final var entry = TestEntry.ofDefaults();
    final var testFailures = new ConcurrentLinkedQueue<Throwable>();

    // when -- start a single writer thread
    final var writer =
        newWriterThread(sequencer, 1, 100_000, List.of(entry), true, testFailures::add);
    writer.start();
    writer.join();

    // then -- VerifyingLogStorage did not throw
    Assertions.assertThat(testFailures).isEmpty();
  }

  @Test
  void maintainsPositionWithMultipleWritersAndSingleEntry() throws InterruptedException {
    // given
    final var numberOfWriters = 8;
    final var logStorage = new VerifyingLogStorage();
    final var logStreamMetrics = new LogStreamMetrics(new SimpleMeterRegistry());
    final var sequencer =
        new Sequencer(
            logStorage,
            1,
            16,
            InstantSource.system(),
            new SequencerMetrics(new SimpleMeterRegistry()),
            new FlowControl(logStreamMetrics));
    final var entry = TestEntry.ofDefaults();
    final var testFailures = new ConcurrentLinkedQueue<Throwable>();

    // when -- start a single writer thread
    final var writers = new Thread[numberOfWriters];
    for (int i = 0; i < numberOfWriters; i++) {
      writers[i] = newWriterThread(sequencer, 1, 100_000, List.of(entry), false, testFailures::add);
    }
    for (final var writer : writers) {
      writer.start();
    }
    for (final var writer : writers) {
      writer.join();
    }

    // then -- VerifyingLogStorage did not throw
    Assertions.assertThat(testFailures).isEmpty();
  }

  @Test
  void maintainsPositionWithSingleWriterAndMultipleEntries() throws InterruptedException {
    // given
    final var logStorage = new VerifyingLogStorage();
    final var logStreamMetrics = new LogStreamMetrics(new SimpleMeterRegistry());
    final var sequencer =
        new Sequencer(
            logStorage,
            1,
            16,
            InstantSource.system(),
            new SequencerMetrics(new SimpleMeterRegistry()),
            new FlowControl(logStreamMetrics));
    final var entries =
        List.of(TestEntry.ofDefaults(), TestEntry.ofDefaults(), TestEntry.ofDefaults());
    final var testFailures = new ConcurrentLinkedQueue<Throwable>();

    // when -- start a single writer thread
    final var writer = newWriterThread(sequencer, 1, 100_000, entries, true, testFailures::add);
    writer.start();
    writer.join();

    // then -- VerifyingLogStorage did not throw
    Assertions.assertThat(testFailures).isEmpty();
  }

  @Test
  void maintainsPositionWithMultipleWritersAndMultipleEntries() throws InterruptedException {
    // given
    final var numberOfWriters = 8;
    final var logStorage = new VerifyingLogStorage();
    final var logStreamMetrics = new LogStreamMetrics(new SimpleMeterRegistry());
    final var sequencer =
        new Sequencer(
            logStorage,
            1,
            16,
            InstantSource.system(),
            new SequencerMetrics(new SimpleMeterRegistry()),
            new FlowControl(logStreamMetrics));
    final var entries =
        List.of(TestEntry.ofDefaults(), TestEntry.ofDefaults(), TestEntry.ofDefaults());
    final var testFailures = new ConcurrentLinkedQueue<Throwable>();

    // when -- start a single writer thread
    final var writers = new Thread[numberOfWriters];
    for (int i = 0; i < numberOfWriters; i++) {
      writers[i] = newWriterThread(sequencer, 1, 100_000, entries, false, testFailures::add);
    }
    for (final var writer : writers) {
      writer.start();
    }
    for (final var writer : writers) {
      writer.join();
    }

    // then -- VerifyingLogStorage did not throw
    Assertions.assertThat(testFailures).isEmpty();
  }

  private Thread newWriterThread(
      final Sequencer sequencer,
      final long initialPosition,
      final long batchesToWrite,
      final List<LogAppendEntry> batchToWrite,
      final boolean isOnlyWriter,
      final Consumer<Throwable> failedAssertionHandler) {
    final var thread =
        new Thread(
            () -> {
              var batchesWritten = 0L;
              var lastWrittenPosition = initialPosition - 1;
              while (batchesWritten < batchesToWrite) {
                final var result = sequencer.tryWrite(WriteContext.internal(), batchToWrite);
                if (result.isRight()) {
                  if (isOnlyWriter) {
                    Assertions.assertThat(result.get())
                        .isEqualTo(lastWrittenPosition + batchToWrite.size());
                  } else {
                    Assertions.assertThat(result.get()).isGreaterThan(lastWrittenPosition);
                  }
                  lastWrittenPosition = result.get();
                  batchesWritten += 1;
                } else {
                  LockSupport.parkNanos(1_000_000);
                }
              }
            });
    thread.setUncaughtExceptionHandler((t, e) -> failedAssertionHandler.accept(e));

    return thread;
  }

  private static final class VerifyingLogStorage implements LogStorage {

    private long position = -1;

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
      if (position != -1) {
        Assertions.assertThat(lowestPosition).isEqualTo(position + 1);
      }
      position = highestPosition;
      listener.onCommit(position, highestPosition);
    }

    @Override
    public void addCommitListener(final CommitListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeCommitListener(final CommitListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void updateCompactionBound(final long compactionBound) {
      throw new UnsupportedOperationException();
    }
  }
}
