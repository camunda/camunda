/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import static io.zeebe.engine.processor.StreamProcessorServiceNames.streamProcessorService;

import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.servicecontainer.ServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StreamProcessorBuilder {

  private final ProcessingContext processingContext;
  private final List<ServiceName<?>> additionalDependencies = new ArrayList<>();
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private TypedRecordProcessorFactory typedRecordProcessorFactory;
  private ActorScheduler actorScheduler;
  private ServiceContainer serviceContainer;
  private ZeebeDb zeebeDb;

  public StreamProcessorBuilder() {
    processingContext = new ProcessingContext();
  }

  public StreamProcessorBuilder streamProcessorFactory(
      TypedRecordProcessorFactory typedRecordProcessorFactory) {
    this.typedRecordProcessorFactory = typedRecordProcessorFactory;
    return this;
  }

  public StreamProcessorBuilder additionalDependencies(ServiceName<?> additionalDependencies) {
    this.additionalDependencies.add(additionalDependencies);
    return this;
  }

  public StreamProcessorBuilder actorScheduler(ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return this;
  }

  public StreamProcessorBuilder serviceContainer(ServiceContainer serviceContainer) {
    this.serviceContainer = serviceContainer;
    return this;
  }

  public StreamProcessorBuilder logStream(LogStream stream) {
    processingContext.logStream(stream);
    return this;
  }

  public StreamProcessorBuilder commandResponseWriter(CommandResponseWriter commandResponseWriter) {
    processingContext.commandResponseWriter(commandResponseWriter);
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

  public ServiceContainer getServiceContainer() {
    return serviceContainer;
  }

  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return lifecycleListeners;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public ActorFuture<StreamProcessor> build() {
    validate();

    final LogStream logStream = processingContext.getLogStream();
    processingContext
        .logStreamReader(new BufferedLogStreamReader(logStream))
        .logStreamWriter(new TypedStreamWriterImpl(logStream));

    final MetadataFilter metadataFilter = new VersionFilter();
    final EventFilter eventFilter = new MetadataEventFilter(metadataFilter);
    processingContext.eventFilter(eventFilter);

    final StreamProcessor streamProcessor = new StreamProcessor(this);

    final String logName = logStream.getLogName();

    final ServiceName<StreamProcessor> serviceName = streamProcessorService(logName);
    final ServiceBuilder<StreamProcessor> serviceBuilder =
        serviceContainer
            .createService(serviceName, streamProcessor)
            .dependency(LogStreamServiceNames.logStreamServiceName(logName))
            .dependency(LogStreamServiceNames.logWriteBufferServiceName(logName))
            .dependency(LogStreamServiceNames.logStorageServiceName(logName));

    if (additionalDependencies != null) {
      additionalDependencies.forEach((d) -> serviceBuilder.dependency(d));
    }

    return serviceBuilder.install();
  }

  private void validate() {
    Objects.requireNonNull(typedRecordProcessorFactory, "No stream processor factory provided.");
    Objects.requireNonNull(actorScheduler, "No task scheduler provided.");
    Objects.requireNonNull(serviceContainer, "No service container provided.");
    Objects.requireNonNull(processingContext.getLogStream(), "No log stream provided.");
    Objects.requireNonNull(
        processingContext.getCommandResponseWriter(), "No command response writer provided.");
    Objects.requireNonNull(zeebeDb, "No database provided.");
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
