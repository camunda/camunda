/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static java.util.Objects.requireNonNull;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.NoopScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class StreamProcessorBuilder {

  private final StreamProcessorContext streamProcessorContext;
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private @Nullable ActorSchedulingService actorSchedulingService;
  private @Nullable ZeebeDb zeebeDb;

  private @Nullable List<RecordProcessor> recordProcessors;
  private StageableScheduledCommandCache scheduledCommandCache = new NoopScheduledCommandCache();

  public StreamProcessorBuilder() {
    streamProcessorContext = new StreamProcessorContext();
  }

  public StreamProcessorBuilder recordProcessors(final List<RecordProcessor> recordProcessors) {
    this.recordProcessors = recordProcessors;
    return this;
  }

  public StreamProcessorBuilder actorSchedulingService(
      final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
    return this;
  }

  public StreamProcessorBuilder logStream(final LogStream stream) {
    streamProcessorContext.logStream(stream);
    return this;
  }

  public StreamProcessorBuilder partitionId(final PartitionId partitionId) {
    streamProcessorContext.partitionId(partitionId);
    return this;
  }

  public StreamProcessorBuilder commandResponseWriter(
      final CommandResponseWriter commandResponseWriter) {
    streamProcessorContext.commandResponseWriter(commandResponseWriter);
    return this;
  }

  public StreamProcessorBuilder listener(final StreamProcessorListener listener) {
    streamProcessorContext.listener(listener);
    return this;
  }

  public StreamProcessorBuilder zeebeDb(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
    return this;
  }

  public StreamProcessorBuilder streamProcessorMode(final StreamProcessorMode streamProcessorMode) {
    streamProcessorContext.processorMode(streamProcessorMode);
    return this;
  }

  public StreamProcessorBuilder partitionCommandSender(
      final InterPartitionCommandSender interPartitionCommandSender) {
    streamProcessorContext.partitionCommandSender(interPartitionCommandSender);
    return this;
  }

  public StreamProcessorContext getProcessingContext() {
    return streamProcessorContext;
  }

  public ActorSchedulingService getActorSchedulingService() {
    return requireNonNull(actorSchedulingService);
  }

  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return Collections.unmodifiableList(lifecycleListeners);
  }

  public StreamProcessorBuilder addLifecycleListener(
      final StreamProcessorLifecycleAware lifecycleAware) {
    lifecycleListeners.add(lifecycleAware);
    return this;
  }

  public ZeebeDb<?> getZeebeDb() {
    return requireNonNull(zeebeDb);
  }

  public List<RecordProcessor> getRecordProcessors() {
    return requireNonNull(recordProcessors);
  }

  public StreamProcessorBuilder scheduledCommandCache(
      final StageableScheduledCommandCache scheduledCommandCache) {
    this.scheduledCommandCache = scheduledCommandCache;
    return this;
  }

  public StageableScheduledCommandCache scheduledCommandCache() {
    return scheduledCommandCache;
  }

  public StreamProcessor build() {
    validate();

    return new StreamProcessor(this);
  }

  private void validate() {
    requireNonNull(actorSchedulingService, "No task scheduler provided.");
    requireNonNull(streamProcessorContext.getLogStream(), "No log stream provided.");
    requireNonNull(zeebeDb, "No database provided.");
    requireNonNull(recordProcessors, "Record processors cannot be empty.");
    if (streamProcessorContext.getProcessorMode() == StreamProcessorMode.PROCESSING) {
      requireNonNull(
          streamProcessorContext.getPartitionCommandSender(),
          "No partition command sender provided");
    }
    if (streamProcessorContext.getMaxCommandsInBatch() < 1) {
      throw new IllegalArgumentException(
          "Batch processing limit must be >= 1 but was %s"
              .formatted(streamProcessorContext.getMaxCommandsInBatch()));
    }
    if (streamProcessorContext.getMaxRecoverableRetries() < 1) {
      throw new IllegalArgumentException(
          "maxRecoverableRetries must be >= 1 but was %s"
              .formatted(streamProcessorContext.getMaxRecoverableRetries()));
    }
    final var maxBatchProcessingTime = streamProcessorContext.getMaxBatchProcessingTime();
    if (maxBatchProcessingTime != null && !maxBatchProcessingTime.isPositive()) {
      throw new IllegalArgumentException(
          "maxBatchProcessingTime must be positive but was %s".formatted(maxBatchProcessingTime));
    }
  }

  public StreamProcessorBuilder maxCommandsInBatch(final int maxCommandsInBatch) {
    streamProcessorContext.maxCommandsInBatch(maxCommandsInBatch);
    return this;
  }

  public StreamProcessorBuilder maxBatchProcessingTime(final Duration maxBatchProcessingTime) {
    streamProcessorContext.maxBatchProcessingTime(maxBatchProcessingTime);
    return this;
  }

  public StreamProcessorBuilder maxRecoverableRetries(final int maxRecoverableRetries) {
    streamProcessorContext.maxRecoverableRetries(maxRecoverableRetries);
    return this;
  }

  public StreamProcessorBuilder processingFilter(final EventFilter processingFilter) {
    streamProcessorContext.processingFilter(processingFilter);
    return this;
  }

  public StreamProcessorBuilder clock(final ControllableStreamClock clock) {
    streamProcessorContext.clock(clock);
    return this;
  }

  public StreamProcessorBuilder meterRegistry(
      final io.micrometer.core.instrument.MeterRegistry meterRegistry) {
    streamProcessorContext.meterRegistry(meterRegistry);
    return this;
  }

  public StreamProcessorBuilder setScheduledTaskCheckInterval(
      final Duration scheduledTaskCheckInterval) {
    streamProcessorContext.setScheduledTaskCheckInterval(scheduledTaskCheckInterval);
    return this;
  }
}
