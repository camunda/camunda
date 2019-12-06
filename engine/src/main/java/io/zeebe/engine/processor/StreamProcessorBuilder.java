/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.util.sched.ActorScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class StreamProcessorBuilder {

  private final ProcessingContext processingContext;
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private TypedRecordProcessorFactory typedRecordProcessorFactory;
  private ActorScheduler actorScheduler;
  private ZeebeDb zeebeDb;
  private LogStreamBatchWriter batchWriter;
  private LogStorage logStorage;

  public StreamProcessorBuilder() {
    processingContext = new ProcessingContext();
  }

  public StreamProcessorBuilder streamProcessorFactory(
      TypedRecordProcessorFactory typedRecordProcessorFactory) {
    this.typedRecordProcessorFactory = typedRecordProcessorFactory;
    return this;
  }

  public StreamProcessorBuilder actorScheduler(ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return this;
  }

  public StreamProcessorBuilder logStream(LogStream stream) {
    processingContext.logStream(stream);
    return this;
  }

  public StreamProcessorBuilder maxFragmentSize(int maxFragmentSize) {
    processingContext.maxFragmentSize(maxFragmentSize);
    return this;
  }

  public StreamProcessorBuilder batchWriter(LogStreamBatchWriter batchWriter) {
    this.batchWriter = batchWriter;
    return this;
  }

  public StreamProcessorBuilder logStorage(LogStorage logStorage) {
    this.logStorage = logStorage;
    return this;
  }

  public StreamProcessorBuilder commandResponseWriter(CommandResponseWriter commandResponseWriter) {
    processingContext.commandResponseWriter(commandResponseWriter);
    return this;
  }

  public StreamProcessorBuilder onProcessedListener(Consumer<TypedRecord> onProcessed) {
    processingContext.onProcessedListener(onProcessed);
    return this;
  }

  public StreamProcessorBuilder zeebeDb(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
    return this;
  }

  public TypedRecordProcessorFactory getTypedRecordProcessorFactory() {
    return typedRecordProcessorFactory;
  }

  public ProcessingContext getProcessingContext() {
    return processingContext;
  }

  public ActorScheduler getActorScheduler() {
    return actorScheduler;
  }

  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return lifecycleListeners;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public StreamProcessor build() {
    validate();

    processingContext
        .logStreamReader(new BufferedLogStreamReader(logStorage))
        .logStreamWriter(new TypedStreamWriterImpl(batchWriter));

    final MetadataFilter metadataFilter = new VersionFilter();
    final EventFilter eventFilter = new MetadataEventFilter(metadataFilter);
    processingContext.eventFilter(eventFilter);

    return new StreamProcessor(this);
  }

  private void validate() {
    Objects.requireNonNull(typedRecordProcessorFactory, "No stream processor factory provided.");
    Objects.requireNonNull(actorScheduler, "No task scheduler provided.");
    Objects.requireNonNull(processingContext.getLogStream(), "No log stream provided.");
    Objects.requireNonNull(
        processingContext.getCommandResponseWriter(), "No command response writer provided.");
    Objects.requireNonNull(zeebeDb, "No database provided.");
    Objects.requireNonNull(logStorage, "No log storage provided.");
    Objects.requireNonNull(batchWriter, "No batcher writer provided.");
  }

  private static class MetadataEventFilter implements EventFilter {

    protected final RecordMetadata metadata = new RecordMetadata();
    protected final MetadataFilter metadataFilter;

    MetadataEventFilter(MetadataFilter metadataFilter) {
      this.metadataFilter = metadataFilter;
    }

    @Override
    public boolean applies(LoggedEvent event) {
      event.readMetadata(metadata);
      return metadataFilter.applies(metadata);
    }
  }

  private final class VersionFilter implements MetadataFilter {
    @Override
    public boolean applies(RecordMetadata m) {
      if (m.getProtocolVersion() > Protocol.PROTOCOL_VERSION) {
        throw new RuntimeException(
            String.format(
                "Cannot handle event with version newer "
                    + "than what is implemented by broker (%d > %d)",
                m.getProtocolVersion(), Protocol.PROTOCOL_VERSION));
      }

      return true;
    }
  }
}
