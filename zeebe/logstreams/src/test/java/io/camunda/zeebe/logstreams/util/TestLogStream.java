/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.awaitility.Awaitility;

/**
 * Wrapper of {@link LogStream} with some helpful blocking methods for testing purposes.
 *
 * <p>Delegates all {@link LogStream} method to the underlying {@link LogStream}
 */
public class TestLogStream implements LogStream {

  private final LogStream logStream;
  private long lastWrittenPosition = -1;

  public TestLogStream(final LogStream logStream) {
    this.logStream = logStream;
  }

  public static TestLogStreamBuilder builder() {
    return new TestLogStreamBuilder();
  }

  public static TestLogStreamBuilder builder(final LogStreamBuilder builder) {
    return new TestLogStreamBuilder(builder);
  }

  /**
   * @return the current commit position, or a negative value if no entry is committed.
   */
  public long getLastWrittenPosition() {
    return lastWrittenPosition;
  }

  /** sets the new commit position * */
  public void setLastWrittenPosition(final long position) {
    lastWrittenPosition = position;
  }

  /**
   * @return a wrapped {@link #newLogStreamWriter()} which ensures that every write returns only
   *     when the entry has been added to the underlying storage.
   */
  public BlockingLogStreamWriter newBlockingLogStreamWriter() {
    return new BlockingLogStreamWriter(newLogStreamWriter());
  }

  /**
   * Force waiting until the given position has been persisted in the underlying storage.
   *
   * @param position the written position to wait for
   */
  public void awaitPositionWritten(final long position) {
    Awaitility.await("until position " + position + " is written")
        .atMost(Duration.ofSeconds(5))
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(50))
        .pollInSameThread()
        .until(this::getLastWrittenPosition, p -> p >= position);
  }

  @Override
  public void close() {
    logStream.close();
  }

  @Override
  public int getPartitionId() {
    return logStream.getPartitionId();
  }

  @Override
  public String getLogName() {
    return logStream.getLogName();
  }

  @Override
  public LogStreamReader newLogStreamReader() {
    return logStream.newLogStreamReader();
  }

  @Override
  public LogStreamWriter newLogStreamWriter() {
    return logStream.newLogStreamWriter();
  }

  @Override
  public FlowControl getFlowControl() {
    return logStream.getFlowControl();
  }

  @Override
  public void registerRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    logStream.registerRecordAvailableListener(recordAwaiter);
  }

  @Override
  public void removeRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    logStream.removeRecordAvailableListener(recordAwaiter);
  }

  private Either<WriteFailure, Long> syncTryWrite(
      final Supplier<Either<WriteFailure, Long>> writeOperation) {
    final var written =
        Awaitility.await("until dispatcher accepts writer")
            .pollDelay(Duration.ZERO)
            .pollInterval(Duration.ofMillis(50))
            .pollInSameThread()
            .until(writeOperation::get, Either::isRight);

    final var position = written.get();
    // 0 is a special position which is returned when a 'write' is "skipped"
    if (position > 0) {
      awaitPositionWritten(position);
    }

    return written;
  }

  /**
   * A {@link LogStreamWriter} implementation which only returns when the entry has been written to
   * the underlying storage.
   */
  public final class BlockingLogStreamWriter implements LogStreamWriter {
    private final LogStreamWriter delegate;

    private BlockingLogStreamWriter(final LogStreamWriter delegate) {
      this.delegate = delegate;
    }

    @Override
    public Either<WriteFailure, Long> tryWrite(
        final WriteContext context,
        final List<LogAppendEntry> appendEntries,
        final long sourcePosition) {
      return syncTryWrite(() -> delegate.tryWrite(context, appendEntries, sourcePosition));
    }
  }
}
