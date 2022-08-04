/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorListener;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedStreamWriterImpl;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class StreamProcessorBuilder {

  private final StreamProcessorContext streamProcessorContext;
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private TypedRecordProcessorFactory typedRecordProcessorFactory;
  private ActorSchedulingService actorSchedulingService;
  private ZeebeDb zeebeDb;
  private Function<MutableZeebeState, EventApplier> eventApplierFactory;
  private int nodeId;
  private Function<LogStreamBatchWriter, LegacyTypedStreamWriter> typedStreamWriterFactory =
      LegacyTypedStreamWriterImpl::new;

  private RecordProcessor recordProcessor;

  public StreamProcessorBuilder() {
    streamProcessorContext = new StreamProcessorContext();
  }

  public StreamProcessorBuilder streamProcessorFactory(
      final TypedRecordProcessorFactory typedRecordProcessorFactory) {
    this.typedRecordProcessorFactory = typedRecordProcessorFactory;
    return this;
  }

  public StreamProcessorBuilder recordProcessor(final RecordProcessor recordProcessor) {
    this.recordProcessor = recordProcessor;
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

  public StreamProcessorBuilder eventApplierFactory(
      final Function<MutableZeebeState, EventApplier> eventApplierFactory) {
    this.eventApplierFactory = eventApplierFactory;
    return this;
  }

  public StreamProcessorBuilder streamProcessorMode(final StreamProcessorMode streamProcessorMode) {
    streamProcessorContext.processorMode(streamProcessorMode);
    return this;
  }

  public TypedRecordProcessorFactory getTypedRecordProcessorFactory() {
    return typedRecordProcessorFactory;
  }

  public StreamProcessorContext getProcessingContext() {
    return streamProcessorContext;
  }

  public ActorSchedulingService getActorSchedulingService() {
    return actorSchedulingService;
  }

  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return lifecycleListeners;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public int getNodeId() {
    return nodeId;
  }

  public RecordProcessor getRecordProcessor() {
    return recordProcessor;
  }

  public Function<MutableZeebeState, EventApplier> getEventApplierFactory() {
    return eventApplierFactory;
  }

  public StreamProcessor build() {
    validate();

    return new StreamProcessor(this);
  }

  private void validate() {
    Objects.requireNonNull(typedRecordProcessorFactory, "No stream processor factory provided.");
    Objects.requireNonNull(actorSchedulingService, "No task scheduler provided.");
    Objects.requireNonNull(streamProcessorContext.getLogStream(), "No log stream provided.");
    Objects.requireNonNull(zeebeDb, "No database provided.");
    Objects.requireNonNull(eventApplierFactory, "No factory for the event supplier provided.");
  }

  public Function<LogStreamBatchWriter, LegacyTypedStreamWriter> getTypedStreamWriterFactory() {
    return typedStreamWriterFactory;
  }

  public StreamProcessorBuilder typedStreamWriterFactory(
      final Function<LogStreamBatchWriter, LegacyTypedStreamWriter> typedStreamWriterFactory) {
    this.typedStreamWriterFactory = typedStreamWriterFactory;
    return this;
  }
}
