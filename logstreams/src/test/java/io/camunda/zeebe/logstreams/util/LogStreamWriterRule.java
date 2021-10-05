/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import java.time.Duration;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.junit.rules.ExternalResource;

public final class LogStreamWriterRule extends ExternalResource {
  private final LogStreamRule logStreamRule;

  private SynchronousLogStream logStream;
  private LogStreamBatchWriter logStreamWriter;

  public LogStreamWriterRule(final LogStreamRule logStreamRule) {
    this.logStreamRule = logStreamRule;
  }

  @Override
  protected void before() {
    logStream = logStreamRule.getLogStream();
    logStreamWriter = logStream.newLogStreamBatchWriter();
  }

  @Override
  protected void after() {
    closeWriter();
    logStream = null;
  }

  public void closeWriter() {
    logStreamWriter = null;
  }

  public long writeEvents(final int eventCount, final DirectBuffer event) {
    final int defaultBatchSize = 2;
    final int batchCount = (int) Math.ceil(eventCount / (double) defaultBatchSize);
    return writeEvents(batchCount, defaultBatchSize, event);
  }

  public long writeEvents(final int batchCount, final int batchSize, final DirectBuffer event) {
    long lastPosition = -1;

    for (int batch = 0; batch < batchCount; batch++) {
      for (int i = 1; i <= batchSize; i++) {
        final LogEntryBuilder eventWriter = logStreamWriter.event();
        eventWriter.key(i + batch * batchSize).value(event).done();
      }

      // when writing too fast, it can happen that the dispatcher is full too quickly, so we may
      // need to retry
      lastPosition =
          Awaitility.await("until batch is written")
              .atMost(Duration.ofSeconds(5))
              .pollDelay(Duration.ofNanos(0))
              .pollInSameThread()
              .until(logStreamWriter::tryWrite, position -> position >= 0);
    }

    waitForPositionToBeWritten(lastPosition);
    return lastPosition;
  }

  public long writeEvent(final DirectBuffer event) {
    return writeEvent(w -> w.value(event));
  }

  public long writeEvent(final Consumer<LogEntryBuilder> writer) {
    final long position = writeEventInternal(writer);

    waitForPositionToBeWritten(position);

    return position;
  }

  public LogStreamWriterRule sourceEventPosition(final long sourceEventPosition) {
    logStreamWriter.sourceRecordPosition(sourceEventPosition);
    return this;
  }

  private long writeEventInternal(final Consumer<LogEntryBuilder> writer) {
    long position;
    do {
      position = tryWrite(writer);
    } while (position == -1);

    return position;
  }

  public long tryWrite(final Consumer<LogEntryBuilder> writer) {
    final LogEntryBuilder eventWriter = logStreamWriter.event();
    writer.accept(eventWriter);
    eventWriter.done();

    return logStreamWriter.tryWrite();
  }

  public void waitForPositionToBeWritten(final long position) {
    Awaitility.await("until position " + position + " is committed")
        .atMost(Duration.ofSeconds(5))
        .pollDelay(Duration.ofNanos(0))
        .pollInSameThread()
        .until(() -> logStream.getLastWrittenPosition() >= position);
  }
}
