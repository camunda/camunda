/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import io.atomix.protocols.raft.storage.RaftStorage.Builder;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBuilder;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.storage.atomix.AtomixLogStreamBuilder;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public final class LogStreamRule extends ExternalResource {
  private final TemporaryFolder temporaryFolder;
  private final ControlledActorClock clock = new ControlledActorClock();
  private final boolean shouldStartByDefault;

  private Consumer<LogStreamBuilder> streamBuilder;
  private final UnaryOperator<Builder> storageBuilder;
  private LogStream logStream;
  private BufferedLogStreamReader logStreamReader;
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
    this.shouldStartByDefault = shouldStart;
    this.streamBuilder = streamBuilder;
    this.storageBuilder = storageBuilder;
  }

  public static LogStreamRule startByDefault(
      final TemporaryFolder temporaryFolder, final Consumer<LogStreamBuilder> streamBuilder) {
    return new LogStreamRule(temporaryFolder, true, streamBuilder);
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

  public static LogStreamRule createRuleWithoutStarting(final TemporaryFolder temporaryFolder) {
    return new LogStreamRule(temporaryFolder, false, b -> {});
  }

  public LogStream startLogStreamWithConfiguration(final Consumer<LogStreamBuilder> streamBuilder) {
    this.streamBuilder = streamBuilder;
    startLogStream();
    return logStream;
  }

  public LogStream startLogStreamWithStorageConfiguration(final UnaryOperator<Builder> builder) {
    logStorageRule = new AtomixLogStorageRule(temporaryFolder);
    this.logStorageRule.open(builder);
    startLogStream();
    return logStream;
  }

  @Override
  protected void before() {
    actorSchedulerRule = new ActorSchedulerRule(clock);
    actorSchedulerRule.before();

    if (shouldStartByDefault) {
      startLogStream();
    }
  }

  @Override
  protected void after() {
    stopLogStream();

    actorSchedulerRule.after();
  }

  public void startLogStream() {
    final ActorScheduler actorScheduler = actorSchedulerRule.get();

    if (logStorageRule == null) {
      logStorageRule = new AtomixLogStorageRule(temporaryFolder);
      logStorageRule.open(storageBuilder);
    }

    builder =
        new AtomixLogStreamBuilder()
            .withActorScheduler(actorScheduler)
            .withPartitionId(0)
            .withLogName("0")
            .withLogStorage(logStorageRule.getStorage());

    // apply additional configs
    streamBuilder.accept(builder);

    openLogStream();
  }

  public void stopLogStream() {
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

  public void openLogStream() {
    logStream = builder.build();
    logStorageRule.setPositionListener(logStream::setCommitPosition);

    actorSchedulerRule
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarting() {
                actor.runOnCompletionBlockingCurrentPhase(logStream.openAppender(), (v, t) -> {});
              }
            })
        .join();
  }

  public LogStreamReader getLogStreamReader() {
    if (logStream == null) {
      throw new IllegalStateException("Log stream is not open!");
    }

    if (logStreamReader == null) {
      logStreamReader = new BufferedLogStreamReader();
    }
    logStreamReader.wrap(getLogStorage());
    return logStreamReader;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public LogStorage getLogStorage() {
    return logStream.getLogStorage();
  }

  public ControlledActorClock getClock() {
    return clock;
  }
}
