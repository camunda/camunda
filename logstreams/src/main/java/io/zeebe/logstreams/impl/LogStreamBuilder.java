/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl;

import io.zeebe.logstreams.impl.log.LogStreamImpl;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Objects;
import org.agrona.concurrent.status.AtomicLongPosition;

@SuppressWarnings("unchecked")
public class LogStreamBuilder<SELF extends LogStreamBuilder<SELF>> {
  private static final int MINIMUM_FRAGMENT_SIZE = 4 * 1024;
  protected int maxFragmentSize = 1024 * 1024 * 4;
  protected int partitionId = -1;
  protected ActorScheduler actorScheduler;
  protected LogStorage logStorage;
  protected String logName;

  public SELF withActorScheduler(final ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return (SELF) this;
  }

  public SELF withMaxFragmentSize(final int maxFragmentSize) {
    this.maxFragmentSize = maxFragmentSize;
    return (SELF) this;
  }

  public SELF withLogStorage(final LogStorage logStorage) {
    this.logStorage = logStorage;
    return (SELF) this;
  }

  public SELF withPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return (SELF) this;
  }

  public SELF withLogName(final String logName) {
    this.logName = logName;
    return (SELF) this;
  }

  public ActorFuture<LogStream> buildAsync() {
    applyDefaults();
    validate();

    final var logStreamService =
        new LogStreamImpl(
            actorScheduler,
            new ActorConditions(),
            logName,
            partitionId,
            ByteValue.ofBytes(maxFragmentSize),
            new AtomicLongPosition(),
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

  public LogStream build() {
    // this is only called from out test's, but not to implement it multiple times we add the sync
    // build here
    final var buildFuture = new CompletableActorFuture<LogStream>();

    actorScheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarting() {
            actor.runOnCompletionBlockingCurrentPhase(
                buildAsync(),
                (logStream, t) -> {
                  if (t == null) {
                    buildFuture.complete(logStream);
                  } else {
                    buildFuture.completeExceptionally(t);
                  }
                });
          }
        });
    return buildFuture.join();
  }

  protected void applyDefaults() {}

  protected void validate() {
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
