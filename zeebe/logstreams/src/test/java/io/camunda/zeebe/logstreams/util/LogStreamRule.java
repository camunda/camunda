/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Consumer;
import org.agrona.CloseHelper;
import org.junit.rules.ExternalResource;

public final class LogStreamRule extends ExternalResource {
  private final ControlledClock clock = new ControlledClock();
  private final boolean shouldStartByDefault;

  private final Consumer<LogStreamBuilder> streamBuilder;
  private TestLogStream logStream;
  private LogStreamReader logStreamReader;
  private LogStreamWriter logStreamWriter;
  private LogStreamBuilder builder;
  private ListLogStorage listLogStorage;

  private LogStreamRule(final boolean shouldStart, final Consumer<LogStreamBuilder> streamBuilder) {
    shouldStartByDefault = shouldStart;
    this.streamBuilder = streamBuilder;
  }

  public static LogStreamRule startByDefault(final Consumer<LogStreamBuilder> streamBuilder) {
    return new LogStreamRule(true, streamBuilder);
  }

  public static LogStreamRule startByDefault() {
    return new LogStreamRule(true, (b) -> {});
  }

  @Override
  protected void before() {
    if (shouldStartByDefault) {
      createLogStream();
    }
  }

  @Override
  protected void after() {
    stopLogStream();
  }

  public void createLogStream() {
    if (listLogStorage == null) {
      listLogStorage = new ListLogStorage();
    }

    builder =
        LogStream.builder()
            .withPartitionId(0)
            .withLogName("logStream-0")
            .withLogStorage(listLogStorage)
            .withClock(clock);

    // apply additional configs
    streamBuilder.accept(builder);

    openLogStream();
  }

  private void stopLogStream() {
    CloseHelper.quietCloseAll(logStreamReader, logStream);

    logStream = null;
    logStreamReader = null;
    logStreamWriter = null;
    listLogStorage = null;
  }

  private void openLogStream() {
    logStream = TestLogStream.builder(builder).build();
    listLogStorage.setPositionListener(logStream::setLastWrittenPosition);
  }

  public LogStreamReader newLogStreamReader() {
    return logStream.newLogStreamReader();
  }

  public LogStreamReader getLogStreamReader() {
    if (logStream == null) {
      throw new IllegalStateException("Log stream is not open!");
    }

    if (logStreamReader == null) {
      logStreamReader = logStream.newLogStreamReader();
    }

    return logStreamReader;
  }

  public LogStreamWriter getLogStreamWriter() {
    if (logStream == null) {
      throw new IllegalStateException("Log stream is not open!");
    }

    if (logStreamWriter == null) {
      logStreamWriter = logStream.newLogStreamWriter();
    }

    return logStreamWriter;
  }

  public TestLogStream getLogStream() {
    return logStream;
  }

  public ControlledClock getClock() {
    return clock;
  }

  public static final class ControlledClock implements InstantSource {
    public InstantSource delegate;

    @Override
    public Instant instant() {
      return delegate == null ? Instant.now() : delegate.instant();
    }
  }
}
