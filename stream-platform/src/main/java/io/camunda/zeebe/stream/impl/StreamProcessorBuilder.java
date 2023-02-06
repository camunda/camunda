/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StreamProcessorBuilder {

  private final StreamProcessorContext streamProcessorContext;
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private ActorSchedulingService actorSchedulingService;
  private ZeebeDb zeebeDb;
  private int nodeId;

  private List<RecordProcessor> recordProcessors;

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

  public StreamProcessorBuilder nodeId(final int nodeId) {
    this.nodeId = nodeId;
    return this;
  }

  public StreamProcessorBuilder logStream(final LogStream stream) {
    streamProcessorContext.logStream(stream);
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
    return actorSchedulingService;
  }

  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return Collections.unmodifiableList(lifecycleListeners);
  }

  public void addLifecycleListener(final StreamProcessorLifecycleAware lifecycleAware) {
    lifecycleListeners.add(lifecycleAware);
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public int getNodeId() {
    return nodeId;
  }

  public List<RecordProcessor> getRecordProcessors() {
    return recordProcessors;
  }

  public StreamProcessor build() {
    validate();

    return new StreamProcessor(this);
  }

  private void validate() {
    Objects.requireNonNull(actorSchedulingService, "No task scheduler provided.");
    Objects.requireNonNull(streamProcessorContext.getLogStream(), "No log stream provided.");
    Objects.requireNonNull(zeebeDb, "No database provided.");
    if (streamProcessorContext.getProcessorMode() == StreamProcessorMode.PROCESSING) {
      Objects.requireNonNull(
          streamProcessorContext.getPartitionCommandSender(),
          "No partition command sender provided");
    }
    if (streamProcessorContext.getProcessingBatchLimit() < 1) {
      throw new IllegalArgumentException(
          "Batch processing limit must be >= 1 but was %s"
              .formatted(streamProcessorContext.getProcessingBatchLimit()));
    }
  }

  public StreamProcessorBuilder processingBatchLimit(final int processingBatchLimit) {
    streamProcessorContext.processingBatchLimit(processingBatchLimit);
    return this;
  }
}
