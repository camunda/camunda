/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.function.Consumer;
import org.junit.rules.ExternalResource;

public final class LogStreamRule extends ExternalResource {
  private final ControlledActorClock clock = new ControlledActorClock();
  private final boolean shouldStartByDefault;

  private final Consumer<LogStreamBuilder> streamBuilder;
  private SynchronousLogStream logStream;
  private LogStreamReader logStreamReader;
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
            .withLogName("0")
            .withLogStorage(listLogStorage);

    // apply additional configs
    streamBuilder.accept(builder);

    openLogStream();
  }

  private void stopLogStream() {
    if (logStream != null) {
      logStream.close();
    }

    if (logStreamReader != null) {
      logStreamReader.close();
      logStreamReader = null;
    }

    if (listLogStorage != null) {
      listLogStorage = null;
    }
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

  public SynchronousLogStream getLogStream() {
    return logStream;
  }

  public ControlledActorClock getClock() {
    return clock;
  }
}
