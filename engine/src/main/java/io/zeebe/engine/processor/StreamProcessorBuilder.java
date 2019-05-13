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

import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
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

  protected LogStream logStream;

  protected ActorScheduler actorScheduler;

  protected Duration snapshotPeriod;
  protected SnapshotController snapshotController;

  private LogStreamReader logStreamReader;
  protected LogStreamRecordWriter logStreamWriter;

  private EventFilter eventFilter;

  protected ServiceContainer serviceContainer;
  private List<ServiceName<?>> additionalDependencies;
  private StreamProcessorFactory streamProcessorFactory;
  private int maxSnapshots;
  private boolean deleteDataOnSnapshot;

  public StreamProcessorBuilder(int id, String name) {
    this.id = id;
    this.name = name;
  }

  public StreamProcessorBuilder streamProcessorFactory(
      StreamProcessorFactory streamProcessorFactory) {
    this.streamProcessorFactory = streamProcessorFactory;
    return this;
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

  public StreamProcessorBuilder maxSnapshots(int maxSnapshots) {
    this.maxSnapshots = maxSnapshots;
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

  public StreamProcessorBuilder serviceContainer(ServiceContainer serviceContainer) {
    this.serviceContainer = serviceContainer;
    return this;
  }

  public StreamProcessorBuilder deleteDataOnSnapshot(final boolean deleteData) {
    this.deleteDataOnSnapshot = deleteData;
    return this;
  }

  public ActorFuture<StreamProcessorService> build() {
    validate();

    final StreamProcessorContext context = createContext();
    final StreamProcessorController controller = new StreamProcessorController(context);

    final String logName = logStream.getLogName();

    final ServiceName<StreamProcessorService> serviceName = streamProcessorService(logName, name);
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
    Objects.requireNonNull(streamProcessorFactory, "No stream processor factory provided.");
    Objects.requireNonNull(logStream, "No log stream provided.");
    Objects.requireNonNull(actorScheduler, "No task scheduler provided.");
    Objects.requireNonNull(serviceContainer, "No service container provided.");
    Objects.requireNonNull(snapshotController, "No snapshot controller provided.");
  }

  private StreamProcessorContext createContext() {
    final StreamProcessorContext ctx = new StreamProcessorContext();
    ctx.setId(id);
    ctx.setName(name);
    ctx.setStreamProcessorFactory(streamProcessorFactory);

    ctx.setLogStream(logStream);

    ctx.setActorScheduler(actorScheduler);

    ctx.setEventFilter(eventFilter);

    if (snapshotPeriod == null) {
      snapshotPeriod = Duration.ofMinutes(1);
    }

    ctx.setSnapshotPeriod(snapshotPeriod);
    ctx.setMaxSnapshots(maxSnapshots);
    ctx.setSnapshotController(snapshotController);
    ctx.setDeleteDataOnSnapshot(deleteDataOnSnapshot);

    logStreamReader = new BufferedLogStreamReader();
    ctx.setLogStreamReader(logStreamReader);

    logStreamWriter = new LogStreamWriterImpl();
    ctx.setLogStreamWriter(logStreamWriter);

    return ctx;
  }
}
