/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.log;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBuilder;
import io.zeebe.logstreams.storage.LogStorage;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Objects;

public final class LogStreamBuilderImpl implements LogStreamBuilder {
  private static final int MINIMUM_FRAGMENT_SIZE = 4 * 1024;
  private int maxFragmentSize = 1024 * 1024 * 4;
  private int partitionId = -1;
  private ActorScheduler actorScheduler;
  private LogStorage logStorage;
  private String logName;
  private int nodeId = 0;

  @Override
  public LogStreamBuilder withActorScheduler(final ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return this;
  }

  @Override
  public LogStreamBuilder withMaxFragmentSize(final int maxFragmentSize) {
    this.maxFragmentSize = maxFragmentSize;
    return this;
  }

  @Override
  public LogStreamBuilder withLogStorage(final LogStorage logStorage) {
    this.logStorage = logStorage;
    return this;
  }

  @Override
  public LogStreamBuilder withPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public LogStreamBuilder withNodeId(final int nodeId) {
    this.nodeId = nodeId;
    return this;
  }

  @Override
  public LogStreamBuilder withLogName(final String logName) {
    this.logName = logName;
    return this;
  }

  @Override
  public ActorFuture<LogStream> buildAsync() {
    validate();

    final var logStreamService =
        new LogStreamImpl(
            actorScheduler,
            new ActorConditions(),
            logName,
            partitionId,
            nodeId,
            maxFragmentSize,
            logStorage);

    final var logstreamInstallFuture = new CompletableActorFuture<LogStream>();
    actorScheduler
        .submitActor(logStreamService)
        .onComplete(
            (v, t) -> {
              if (t == null) {
                logstreamInstallFuture.complete(logStreamService);
              } else {
                logstreamInstallFuture.completeExceptionally(t);
              }
            });

    return logstreamInstallFuture;
  }

  private void validate() {
    Objects.requireNonNull(actorScheduler, "Must specify a actor scheduler");
    Objects.requireNonNull(logStorage, "Must specify a log storage");

    if (maxFragmentSize < MINIMUM_FRAGMENT_SIZE) {
      throw new IllegalArgumentException(
          String.format(
              "Expected fragment size to be at least '%d', but was '%d'",
              MINIMUM_FRAGMENT_SIZE, maxFragmentSize));
    }
  }
}
