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

import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.JOB_QUEUE_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.JOB_TIME_OUT_STREAM_PROCESSOR_ID;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.job.processor.JobInstanceStreamProcessor;
import io.zeebe.broker.job.processor.JobTimeOutStreamProcessor;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import java.time.Duration;

public class JobQueueManagerService implements Service<JobQueueManagerService> {
  protected static final String NAME = "job.queue.manager";
  public static final Duration TIME_OUT_INTERVAL = Duration.ofSeconds(30);

  private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  private final Injector<JobSubscriptionManager> jobSubscriptionManagerInjector = new Injector<>();
  private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector =
      new Injector<>();

  private final ServiceGroupReference<Partition> partitionsReference =
      ServiceGroupReference.<Partition>create().onAdd(this::addPartition).build();

  private ActorScheduler actorScheduler;
  private StreamProcessorServiceFactory streamProcessorServiceFactory;

  public void startJobQueue(ServiceName<Partition> name, Partition partition) {
    final ServerTransport serverTransport = clientApiTransportInjector.getValue();

    final JobSubscriptionManager jobSubscriptionManager = jobSubscriptionManagerInjector.getValue();

    final JobInstanceStreamProcessor jobInstanceStreamProcessor =
        new JobInstanceStreamProcessor(jobSubscriptionManager);
    final TypedStreamEnvironment env =
        new TypedStreamEnvironment(partition.getLogStream(), serverTransport.getOutput());

    streamProcessorServiceFactory
        .createService(partition, name)
        .processor(jobInstanceStreamProcessor.createStreamProcessor(env))
        .processorId(JOB_QUEUE_STREAM_PROCESSOR_ID)
        .processorName("job-instance")
        .build();

    startTimeOutService(name, partition, env);
  }

  protected void startTimeOutService(
      ServiceName<Partition> partitionServiceName,
      Partition partition,
      TypedStreamEnvironment env) {
    final JobTimeOutStreamProcessor timeOutStreamProcessor = new JobTimeOutStreamProcessor();

    streamProcessorServiceFactory
        .createService(partition, partitionServiceName)
        .processor(timeOutStreamProcessor.createStreamProcessor(env))
        .processorId(JOB_TIME_OUT_STREAM_PROCESSOR_ID)
        .processorName("job-time-out")
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

  public Injector<JobSubscriptionManager> getJobSubscriptionManagerInjector() {
    return jobSubscriptionManagerInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsReference;
  }

  public void addPartition(ServiceName<Partition> name, Partition partition) {
    actorScheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            startJobQueue(name, partition);
          }
        });
  }

  public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector() {
    return streamProcessorServiceFactoryInjector;
  }
}
