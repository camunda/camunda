/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

public final class LogStreamWriterRule extends ExternalResource {
  private final LogStreamRule logStreamRule;

  private SynchronousLogStream logStream;
  private LogStreamRecordWriter logStreamWriter;

  public LogStreamWriterRule(final LogStreamRule logStreamRule) {
    this.logStreamRule = logStreamRule;
  }

  @Override
  protected void before() {
    this.logStream = logStreamRule.getLogStream();
    this.logStreamWriter = logStream.newLogStreamRecordWriter();
  }

  @Override
  protected void after() {
    closeWriter();
    logStream = null;
  }

  public void closeWriter() {
    logStreamWriter = null;
  }

  public long writeEvents(final int count, final DirectBuffer event) {
    long lastPosition = -1;
    for (int i = 1; i <= count; i++) {
      final long key = i;
      try {
        lastPosition = writeEventInternal(w -> w.key(key).value(event)).get(5, TimeUnit.SECONDS);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    waitForPositionToBeAppended(lastPosition);

    return lastPosition;
  }

  public long writeEvent(final DirectBuffer event) {
    return writeEvent(w -> w.value(event));
  }

  public long writeEvent(final Consumer<LogStreamRecordWriter> writer) {
    try {
      final long position = writeEventInternal(writer).get(5, TimeUnit.SECONDS);
      waitForPositionToBeAppended(position);
      return position;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Future<Long> writeEventInternal(final Consumer<LogStreamRecordWriter> writer) {
    Optional<ActorFuture<Long>> optFuture;
    do {
      optFuture = tryWrite(writer);
    } while (optFuture.isEmpty());

    return optFuture.get();
  }

  public Optional<ActorFuture<Long>> tryWrite(final Consumer<LogStreamRecordWriter> writer) {
    writer.accept(logStreamWriter);

    return logStreamWriter.tryWrite();
  }

  public void waitForPositionToBeAppended(final long position) {
    TestUtil.waitUntil(
        () -> logStream.getCommitPosition() >= position, // Now only committed events are appended.
        "Failed to wait for position %d to be appended. Commit position is at %d",
        position,
        logStream.getCommitPosition());
  }
}
