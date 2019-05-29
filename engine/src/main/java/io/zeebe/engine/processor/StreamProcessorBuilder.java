/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

  private TypedRecordProcessorFactory typedRecordProcessorFactory;
  private final ProcessingContext processingContext;

  private ActorScheduler actorScheduler;
  private ServiceContainer serviceContainer;
  private final List<ServiceName<?>> additionalDependencies = new ArrayList<>();
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

  private ZeebeDb zeebeDb;

  public StreamProcessorBuilder(int id, String name) {
    Objects.requireNonNull(name);
    processingContext = new ProcessingContext().producerId(id).streamProcessorName(name);
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

  /** @param eventFilter may be null to accept all events */
  public StreamProcessorBuilder eventFilter(EventFilter eventFilter) {
    processingContext.eventFilter(eventFilter);
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

    final String streamProcessorName = processingContext.getStreamProcessorName();
    final String logName = logStream.getLogName();

    final ServiceName<StreamProcessor> serviceName =
        streamProcessorService(logName, streamProcessorName);
    final ServiceBuilder<StreamProcessor> serviceBuilder =
        serviceContainer
            .createService(serviceName, streamProcessor)
            .dependency(LogStreamServiceNames.logStreamServiceName(logName))
            .dependency(LogStreamServiceNames.logWriteBufferServiceName(logName))
            .dependency(LogStreamServiceNames.logStorageServiceName(logName))
            .dependency(LogStreamServiceNames.logBlockIndexServiceName(logName));

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
