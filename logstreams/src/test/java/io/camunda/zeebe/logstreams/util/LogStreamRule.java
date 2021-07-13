/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.util;

import io.atomix.raft.storage.RaftStorage.Builder;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public final class LogStreamRule extends ExternalResource {
  private final TemporaryFolder temporaryFolder;
  private final ControlledActorClock clock = new ControlledActorClock();
  private final boolean shouldStartByDefault;

  private final Consumer<LogStreamBuilder> streamBuilder;
  private final UnaryOperator<Builder> storageBuilder;
  private SynchronousLogStream logStream;
  private LogStreamReader logStreamReader;
  private LogStreamBuilder builder;
  private ActorSchedulerRule actorSchedulerRule;
  private AtomixLogStorageRule logStorageRule;

  private LogStreamRule(
      final TemporaryFolder temporaryFolder,
      final boolean shouldStart,
      final Consumer<LogStreamBuilder> streamBuilder) {
    this(temporaryFolder, shouldStart, streamBuilder, UnaryOperator.identity());
  }

  private LogStreamRule(
      final TemporaryFolder temporaryFolder,
      final boolean shouldStart,
      final Consumer<LogStreamBuilder> streamBuilder,
      final UnaryOperator<Builder> storageBuilder) {
    this.temporaryFolder = temporaryFolder;
    shouldStartByDefault = shouldStart;
    this.streamBuilder = streamBuilder;
    this.storageBuilder = storageBuilder;
  }

  public static LogStreamRule startByDefault(
      final TemporaryFolder temporaryFolder,
      final Consumer<LogStreamBuilder> streamBuilder,
      final UnaryOperator<Builder> storageBuilder) {
    return new LogStreamRule(temporaryFolder, true, streamBuilder, storageBuilder);
  }

  public static LogStreamRule startByDefault(final TemporaryFolder temporaryFolder) {
    return new LogStreamRule(temporaryFolder, true, b -> {});
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

    if (logStorageRule == null) {
      logStorageRule = new AtomixLogStorageRule(temporaryFolder);
      logStorageRule.open(storageBuilder);
    }

    builder =
        LogStream.builder()
            .withActorSchedulingService(actorScheduler)
            .withPartitionId(0)
            .withLogName("0")
            .withLogStorage(logStorageRule.getStorage());

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

    if (logStorageRule != null) {
      logStorageRule.close();
      logStorageRule = null;
    }
  }

  private void openLogStream() {
    logStream =
        SyncLogStream.builder(builder).withActorSchedulingService(actorSchedulerRule.get()).build();
    logStorageRule.setPositionListener(logStream::setLastWrittenPosition);
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
