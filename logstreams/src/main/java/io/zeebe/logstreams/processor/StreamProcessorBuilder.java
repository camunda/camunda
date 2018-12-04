/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.processor;

import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.DisabledLogStreamWriter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.servicecontainer.ServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class StreamProcessorBuilder {
  protected int id;
  protected String name;

  protected StreamProcessor streamProcessor;

  protected LogStream logStream;

  protected ActorScheduler actorScheduler;

  protected Duration snapshotPeriod;
  protected SnapshotController snapshotController;

  protected LogStreamReader logStreamReader;
  protected LogStreamRecordWriter logStreamWriter;

  protected EventFilter eventFilter;

  protected boolean readOnly;

  protected ServiceContainer serviceContainer;
  private List<ServiceName<?>> additionalDependencies;

  public StreamProcessorBuilder(int id, String name, StreamProcessor streamProcessor) {
    this.id = id;
    this.name = name;
    this.streamProcessor = streamProcessor;
  }

  public StreamProcessorBuilder additionalDependencies(
      List<ServiceName<?>> additionalDependencies) {
    this.additionalDependencies = additionalDependencies;
    return this;
  }

  public StreamProcessorBuilder logStream(LogStream stream) {
    this.logStream = stream;
    return this;
  }

  public StreamProcessorBuilder actorScheduler(ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return this;
  }

  public StreamProcessorBuilder snapshotPeriod(Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
    return this;
  }

  public StreamProcessorBuilder snapshotController(SnapshotController snapshotController) {
    this.snapshotController = snapshotController;
    return this;
  }

  /** @param eventFilter may be null to accept all events */
  public StreamProcessorBuilder eventFilter(EventFilter eventFilter) {
    this.eventFilter = eventFilter;
    return this;
  }

  public StreamProcessorBuilder readOnly(boolean readOnly) {
    this.readOnly = readOnly;
    return this;
  }

  public StreamProcessorBuilder serviceContainer(ServiceContainer serviceContainer) {
    this.serviceContainer = serviceContainer;
    return this;
  }

  public ActorFuture<StreamProcessorService> build() {
    validate();

    final StreamProcessorContext context = createContext();
    final StreamProcessorController controller = new StreamProcessorController(context);

    final String logName = logStream.getLogName();

    final ServiceName<StreamProcessorService> serviceName =
        LogStreamServiceNames.streamProcessorService(logName, name);
    final StreamProcessorService service =
        new StreamProcessorService(controller, serviceContainer, serviceName);
    final ServiceBuilder<StreamProcessorService> serviceBuilder =
        serviceContainer
            .createService(serviceName, service)
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
    Objects.requireNonNull(streamProcessor, "No stream processor provided.");
    Objects.requireNonNull(logStream, "No log stream provided.");
    Objects.requireNonNull(actorScheduler, "No task scheduler provided.");
    Objects.requireNonNull(serviceContainer, "No service container provided.");
    Objects.requireNonNull(snapshotController, "No snapshot controller provided.");
  }

  private StreamProcessorContext createContext() {
    final StreamProcessorContext ctx = new StreamProcessorContext();
    ctx.setId(id);
    ctx.setName(name);
    ctx.setStreamProcessor(streamProcessor);

    ctx.setLogStream(logStream);

    ctx.setActorScheduler(actorScheduler);

    ctx.setEventFilter(eventFilter);
    ctx.setReadOnly(readOnly);

    if (snapshotPeriod == null) {
      snapshotPeriod = Duration.ofMinutes(1);
    }

    ctx.setSnapshotPeriod(snapshotPeriod);
    ctx.setSnapshotController(snapshotController);

    logStreamReader = new BufferedLogStreamReader();
    ctx.setLogStreamReader(logStreamReader);

    if (readOnly) {
      logStreamWriter = new DisabledLogStreamWriter();
    } else {
      logStreamWriter = new LogStreamWriterImpl();
    }
    ctx.setLogStreamWriter(logStreamWriter);

    return ctx;
  }
}
