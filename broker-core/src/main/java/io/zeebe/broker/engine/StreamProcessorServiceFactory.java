/*
 * Zeebe Broker Core
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
package io.zeebe.broker.engine;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.engine.processor.EventFilter;
import io.zeebe.engine.processor.MetadataFilter;
import io.zeebe.engine.processor.StreamProcessorFactory;
import io.zeebe.engine.processor.StreamProcessorService;
import io.zeebe.engine.processor.StreamProcessors;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.EnsureUtil;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class StreamProcessorServiceFactory implements Service<StreamProcessorServiceFactory> {
  private final ServiceContainer serviceContainer;
  private final Duration snapshotPeriod;
  private final int maxSnapshots;
  private ActorScheduler actorScheduler;

  public StreamProcessorServiceFactory(
      ServiceContainer serviceContainer, Duration snapshotPeriod, int maxSnapshots) {
    this.serviceContainer = serviceContainer;
    this.snapshotPeriod = snapshotPeriod;
    this.maxSnapshots = maxSnapshots;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.actorScheduler = startContext.getScheduler();
  }

  @Override
  public StreamProcessorServiceFactory get() {
    return this;
  }

  public Builder createService(Partition partition, ServiceName<Partition> serviceName) {
    return new Builder(partition, serviceName);
  }

  public class Builder {
    private final LogStream logStream;

    private SnapshotController snapshotController;
    private String processorName;
    private int processorId = -1;
    private final List<ServiceName<?>> additionalDependencies = new ArrayList<>();

    protected MetadataFilter customEventFilter;
    private StreamProcessorFactory streamProcessorFactory;
    private boolean enableDeleteData;

    public Builder(Partition partition, ServiceName<Partition> serviceName) {
      this.logStream = partition.getLogStream();
      this.additionalDependencies.add(serviceName);
    }

    public Builder streamProcessorFactory(StreamProcessorFactory streamProcessorFactory) {
      this.streamProcessorFactory = streamProcessorFactory;
      return this;
    }

    public Builder processorId(int processorId) {
      this.processorId = processorId;
      return this;
    }

    public Builder processorName(String processorName) {
      this.processorName = processorName;
      return this;
    }

    public Builder snapshotController(SnapshotController snapshotController) {
      this.snapshotController = snapshotController;
      return this;
    }

    public Builder deleteDataOnSnapshot(final boolean enabled) {
      this.enableDeleteData = enabled;
      return this;
    }

    public ActorFuture<StreamProcessorService> build() {
      EnsureUtil.ensureNotNull("stream processor factory", streamProcessorFactory);
      EnsureUtil.ensureNotNullOrEmpty("processor name", processorName);
      EnsureUtil.ensureGreaterThan("process id", processorId, -1);

      MetadataFilter metadataFilter = new VersionFilter();
      if (customEventFilter != null) {
        metadataFilter = metadataFilter.and(customEventFilter);
      }
      final EventFilter eventFilter = new MetadataEventFilter(metadataFilter);

      return StreamProcessors.createStreamProcessor(processorName, processorId)
          .actorScheduler(actorScheduler)
          .serviceContainer(serviceContainer)
          .snapshotController(snapshotController)
          .snapshotPeriod(snapshotPeriod)
          .maxSnapshots(maxSnapshots)
          .logStream(logStream)
          .eventFilter(eventFilter)
          .additionalDependencies(additionalDependencies)
          .streamProcessorFactory(streamProcessorFactory)
          .deleteDataOnSnapshot(enableDeleteData)
          .build();
    }
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
