/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import com.netflix.concurrency.limits.Limit;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.LogStorage;
<<<<<<< HEAD
import java.time.InstantSource;
||||||| parent of df85a699f31 (refactor: migrate sequencer metrics to micrometer)
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
=======
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
>>>>>>> df85a699f31 (refactor: migrate sequencer metrics to micrometer)
import java.util.Objects;

public final class LogStreamBuilderImpl implements LogStreamBuilder {
  private static final int MINIMUM_FRAGMENT_SIZE = 4 * 1024;
  private int maxFragmentSize = 1024 * 1024 * 4;
  private int partitionId = -1;
  private LogStorage logStorage;
  private String logName;
<<<<<<< HEAD
  private InstantSource clock;
  private Limit requestLimit;
  private RateLimit writeRateLimit;
||||||| parent of df85a699f31 (refactor: migrate sequencer metrics to micrometer)
  private int nodeId = 0;

  @Override
  public LogStreamBuilder withActorSchedulingService(
      final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
    return this;
  }
=======
  private int nodeId = 0;
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Override
  public LogStreamBuilder withActorSchedulingService(
      final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
    return this;
  }
>>>>>>> df85a699f31 (refactor: migrate sequencer metrics to micrometer)

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
  public LogStreamBuilder withLogName(final String logName) {
    this.logName = logName;
    return this;
  }

  @Override
<<<<<<< HEAD
  @Override
  public LogStreamBuilder withClock(final InstantSource clock) {
    this.clock = clock;
    return this;
  }

  @Override
  public LogStreamBuilder withRequestLimit(final Limit requestLimit) {
    this.requestLimit = requestLimit;
    return this;
  }

  @Override
  public LogStreamBuilder withWriteRateLimit(final RateLimit writeRateLimit) {
    this.writeRateLimit = writeRateLimit;
    return this;
  }

  @Override
  public LogStream build() {
||||||| parent of df85a699f31 (refactor: migrate sequencer metrics to micrometer)
  public ActorFuture<LogStream> buildAsync() {
=======
  public LogStreamBuilder withMeterRegistry(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    return this;
  }

  @Override
  public ActorFuture<LogStream> buildAsync() {
>>>>>>> df85a699f31 (refactor: migrate sequencer metrics to micrometer)
    validate();

<<<<<<< HEAD
    return new LogStreamImpl(
        logName, partitionId, maxFragmentSize, logStorage, clock, requestLimit, writeRateLimit);
||||||| parent of df85a699f31 (refactor: migrate sequencer metrics to micrometer)
    final var logStreamService =
        new LogStreamImpl(
            actorSchedulingService, logName, partitionId, nodeId, maxFragmentSize, logStorage);

    final var logstreamInstallFuture = new CompletableActorFuture<LogStream>();
    actorSchedulingService
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
=======
    final var logStreamService =
        new LogStreamImpl(
            actorSchedulingService,
            logName,
            partitionId,
            nodeId,
            maxFragmentSize,
            logStorage,
            meterRegistry);

    final var logstreamInstallFuture = new CompletableActorFuture<LogStream>();
    actorSchedulingService
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
>>>>>>> df85a699f31 (refactor: migrate sequencer metrics to micrometer)
  }

  private void validate() {
    Objects.requireNonNull(logStorage, "Must specify a log storage");
    Objects.requireNonNull(clock, "Must specify a clock source");

    if (maxFragmentSize < MINIMUM_FRAGMENT_SIZE) {
      throw new IllegalArgumentException(
          String.format(
              "Expected fragment size to be at least '%d', but was '%d'",
              MINIMUM_FRAGMENT_SIZE, maxFragmentSize));
    }
  }
}
