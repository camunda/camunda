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
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

public class LogStreamWriterRule extends ExternalResource {
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
    logStreamWriter = null;
    logStream = null;
  }

  public long writeEvents(final int count, final DirectBuffer event) {
    long lastPosition = -1;
    for (int i = 1; i <= count; i++) {
      final long key = i;
      lastPosition = writeEventInternal(w -> w.key(key).value(event));
    }

    waitForPositionToBeAppended(lastPosition);

    return lastPosition;
  }

  public long writeEvent(final DirectBuffer event) {
    return writeEvent(w -> w.value(event));
  }

  public long writeEvent(final Consumer<LogStreamRecordWriter> writer) {
    final long position = writeEventInternal(writer);

    waitForPositionToBeAppended(position);

    return position;
  }

  private long writeEventInternal(final Consumer<LogStreamRecordWriter> writer) {
    long position;
    do {
      position = tryWrite(writer);
    } while (position == -1);

    return position;
  }

  public long tryWrite(final Consumer<LogStreamRecordWriter> writer) {
    writer.accept(logStreamWriter);

    return logStreamWriter.tryWrite();
  }

  public void waitForPositionToBeAppended(final long position) {
    TestUtil.waitUntil(
        () -> logStream.getCommitPosition() >= position, // Now only committed events are appended.
        "Failed to wait for position {} to be appended",
        position);
  }
}
