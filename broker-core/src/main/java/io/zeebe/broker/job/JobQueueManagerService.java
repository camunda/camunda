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
package io.zeebe.broker.job;

import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.JOB_STREAM_PROCESSOR_ID;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;

public class JobQueueManagerService implements Service<JobQueueManagerService> {
  private static final String JOB_STREAM_PROCESSOR_NAME = "job";

  private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector =
      new Injector<>();

  private final ServiceGroupReference<Partition> partitionsReference =
      ServiceGroupReference.<Partition>create().onAdd(this::addPartition).build();

  private ActorScheduler actorScheduler;
  private StreamProcessorServiceFactory streamProcessorServiceFactory;

  public void installJobStreamProcessor(ServiceName<Partition> name, Partition partition) {
    final ServerTransport serverTransport = clientApiTransportInjector.getValue();

    final StateStorage stateStorage =
        partition
            .getStateStorageFactory()
            .create(JOB_STREAM_PROCESSOR_ID, JOB_STREAM_PROCESSOR_NAME);

    final JobStreamProcessor processor = new JobStreamProcessor();
    final TypedStreamEnvironment env =
        new TypedStreamEnvironment(partition.getLogStream(), serverTransport.getOutput());
    final SnapshotController snapshotController = processor.createSnapshotController(stateStorage);

    streamProcessorServiceFactory
        .createService(partition, name)
        .processor(processor.createStreamProcessor(env))
        .processorId(JOB_STREAM_PROCESSOR_ID)
        .processorName(JOB_STREAM_PROCESSOR_NAME)
        .snapshotController(snapshotController)
        .build();
  }

  @Override
  public void start(ServiceStartContext serviceContext) {
    actorScheduler = serviceContext.getScheduler();
    streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();
  }

  @Override
  public void stop(ServiceStopContext ctx) {}

  @Override
  public JobQueueManagerService get() {
    return this;
  }

  public Injector<ServerTransport> getClientApiTransportInjector() {
    return clientApiTransportInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsReference;
  }

  public void addPartition(ServiceName<Partition> name, Partition partition) {
    actorScheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            installJobStreamProcessor(name, partition);
          }
        });
  }

  public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector() {
    return streamProcessorServiceFactoryInjector;
  }
}
