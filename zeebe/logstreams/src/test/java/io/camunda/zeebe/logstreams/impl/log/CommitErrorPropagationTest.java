/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.time.InstantSource;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Integration test that verifies end-to-end commit error propagation through the real Sequencer and
 * FlowControl: LogStorage.append → FlowControl.onCommitError → CommitErrorHandler.
 *
 * <p>This test connects a real {@link Sequencer} and {@link FlowControl} through an error-injecting
 * {@link LogStorage}, verifying that when a commit error occurs (e.g., leader stepping down), the
 * registered {@link FlowControl.CommitErrorHandler} is invoked with the correct request metadata so
 * the client receives a failed response instead of timing out.
 */
@Execution(ExecutionMode.CONCURRENT)
final class CommitErrorPropagationTest {

  @Test
  void shouldPropagateCommitErrorToHandlerForUserCommand() throws InterruptedException {
    // given - a Sequencer connected to FlowControl with a CommitErrorHandler
    final var meterRegistry = new SimpleMeterRegistry();
    final var logStreamMetrics = new LogStreamMetrics(meterRegistry);
    final var flowControl = new FlowControl(logStreamMetrics);

    final var capturedRequestId = new AtomicLong(-1);
    final var capturedStreamId = new AtomicInteger(-1);
    final var capturedError = new AtomicReference<Throwable>();
    final var errorLatch = new CountDownLatch(1);
    flowControl.setCommitErrorHandler(
        (requestId, requestStreamId, error) -> {
          capturedRequestId.set(requestId);
          capturedStreamId.set(requestStreamId);
          capturedError.set(error);
          errorLatch.countDown();
        });

    final var commitError = new RuntimeException("Leader stepping down");
    final var logStorage = new CommitErrorLogStorage(commitError);
    final var sequencer =
        new Sequencer(
            logStorage,
            1L,
            4 * 1024 * 1024,
            InstantSource.system(),
            new SequencerMetrics(meterRegistry),
            flowControl);

    // when - write a user command with request metadata through the full chain
    final var writeResult =
        sequencer.tryWrite(
            WriteContext.userCommand(JobIntent.COMPLETE, 42L, 7),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.JOB)
                    .intent(JobIntent.COMPLETE),
                new UnifiedRecordValue(0)));

    // then - the write should succeed (entry appended to storage)
    assertThat(writeResult.isRight()).isTrue();

    // and the commit error handler should be invoked with the correct request metadata
    assertThat(errorLatch.await(5, TimeUnit.SECONDS))
        .as("commit error handler should be invoked within 5 seconds")
        .isTrue();
    assertThat(capturedRequestId.get()).isEqualTo(42L);
    assertThat(capturedStreamId.get()).isEqualTo(7);
    assertThat(capturedError.get()).isSameAs(commitError);

    sequencer.close();
  }

  @Test
  void shouldNotPropagateCommitErrorToHandlerForInternalCommand() throws InterruptedException {
    // given - a Sequencer connected to FlowControl with a CommitErrorHandler
    final var meterRegistry = new SimpleMeterRegistry();
    final var logStreamMetrics = new LogStreamMetrics(meterRegistry);
    final var flowControl = new FlowControl(logStreamMetrics);

    final var handlerCalled = new CountDownLatch(1);
    flowControl.setCommitErrorHandler(
        (requestId, requestStreamId, error) -> handlerCalled.countDown());

    final var commitError = new RuntimeException("Leader stepping down");
    final var logStorage = new CommitErrorLogStorage(commitError);
    final var sequencer =
        new Sequencer(
            logStorage,
            1L,
            4 * 1024 * 1024,
            InstantSource.system(),
            new SequencerMetrics(meterRegistry),
            flowControl);

    // when - write an internal command (no request metadata)
    final var writeResult =
        sequencer.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.JOB)
                    .intent(JobIntent.COMPLETE),
                new UnifiedRecordValue(0)));

    // then - the write should succeed
    assertThat(writeResult.isRight()).isTrue();

    // but the commit error handler should NOT be called (internal entries have requestId = -1)
    assertThat(handlerCalled.await(500, TimeUnit.MILLISECONDS))
        .as("commit error handler should NOT be invoked for internal commands")
        .isFalse();

    sequencer.close();
  }

  /**
   * A minimal LogStorage that calls onWrite followed by onCommitError instead of onCommit. This
   * simulates what happens in the Raft layer when a leader steps down with pending appends: entries
   * are written locally but the commit fails because the leader can no longer replicate.
   */
  private static final class CommitErrorLogStorage implements LogStorage {

    private final Throwable commitError;
    private final Set<CommitListener> commitListeners = new HashSet<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    CommitErrorLogStorage(final Throwable commitError) {
      this.commitError = commitError;
    }

    @Override
    public LogStorageReader newReader() {
      return new EmptyReader();
    }

    @Override
    public void append(
        final long lowestPosition,
        final long highestPosition,
        final BufferWriter bufferWriter,
        final AppendListener listener) {
      final var buffer = ByteBuffer.allocate(bufferWriter.getLength());
      bufferWriter.write(new UnsafeBuffer(buffer), 0);
      append(lowestPosition, highestPosition, buffer, listener);
    }

    @Override
    public void append(
        final long lowestPosition,
        final long highestPosition,
        final ByteBuffer blockBuffer,
        final AppendListener listener) {
      final var index = currentIndex.getAndIncrement();
      // Simulate the normal onWrite callback
      listener.onWrite(index, highestPosition);
      // Then simulate a commit error (e.g., leader stepping down)
      listener.onCommitError(index, highestPosition, commitError);
    }

    @Override
    public void addCommitListener(final CommitListener listener) {
      commitListeners.add(listener);
    }

    @Override
    public void removeCommitListener(final CommitListener listener) {
      commitListeners.remove(listener);
    }

    private static final class EmptyReader implements LogStorageReader {
      @Override
      public void seek(final long position) {}

      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return false;
      }

      @Override
      public DirectBuffer next() {
        throw new NoSuchElementException();
      }
    }
  }
}
