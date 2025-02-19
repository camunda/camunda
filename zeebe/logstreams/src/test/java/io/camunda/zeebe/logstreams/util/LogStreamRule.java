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
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.Consumer;
import org.agrona.CloseHelper;
import org.junit.rules.ExternalResource;

public final class LogStreamRule extends ExternalResource {
  private final ControlledActorClock clock = new ControlledActorClock();
  private final boolean shouldStartByDefault;

  private final Consumer<LogStreamBuilder> streamBuilder;
  private SynchronousLogStream logStream;
  private LogStreamReader logStreamReader;
  private LogStreamWriter logStreamWriter;
  private LogStreamBuilder builder;
  private ActorSchedulerRule actorSchedulerRule;
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
    actorSchedulerRule = new ActorSchedulerRule(clock);
    actorSchedulerRule.before();

    if (shouldStartByDefault) {
      createLogStream();
    }
  }

  @Override
  protected void after() {
    stopLogStream();

    actorSchedulerRule.after();
  }

  public void createLogStream() {
    final ActorScheduler actorScheduler = actorSchedulerRule.get();

    if (listLogStorage == null) {
      listLogStorage = new ListLogStorage();
    }

    builder =
        LogStream.builder()
            .withActorSchedulingService(actorScheduler)
            .withPartitionId(0)
            .withLogName("logStream-0")
            .withLogStorage(listLogStorage)
            .withClock(clock)
            .withMeterRegistry(new SimpleMeterRegistry());

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
    logStream =
        SyncLogStream.builder(builder).withActorSchedulingService(actorSchedulerRule.get()).build();
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

  public SynchronousLogStream getLogStream() {
    return logStream;
  }

  public ControlledActorClock getClock() {
    return clock;
  }
}
