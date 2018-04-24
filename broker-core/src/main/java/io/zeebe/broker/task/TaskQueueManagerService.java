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
package io.zeebe.broker.task;

import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_QUEUE_STREAM_PROCESSOR_ID;

import java.time.Duration;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.task.processor.TaskExpireLockStreamProcessor;
import io.zeebe.broker.task.processor.TaskInstanceStreamProcessor;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;

public class TaskQueueManagerService implements Service<TaskQueueManagerService>
{
    protected static final String NAME = "task.queue.manager";
    public static final Duration LOCK_EXPIRATION_INTERVAL = Duration.ofSeconds(30);

    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<TaskSubscriptionManager> taskSubscriptionManagerInjector = new Injector<>();
    private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector = new Injector<>();

    private final ServiceGroupReference<Partition> partitionsReference = ServiceGroupReference.<Partition>create()
            .onAdd(this::addPartition)
            .build();

    private ActorScheduler actorScheduler;
    private StreamProcessorServiceFactory streamProcessorServiceFactory;

    public void startTaskQueue(ServiceName<Partition> name, Partition partition)
    {
        final ServerTransport serverTransport = clientApiTransportInjector.getValue();

        final TaskSubscriptionManager taskSubscriptionManager = taskSubscriptionManagerInjector.getValue();

        final TaskInstanceStreamProcessor taskInstanceStreamProcessor = new TaskInstanceStreamProcessor(taskSubscriptionManager);
        final TypedStreamEnvironment env = new TypedStreamEnvironment(partition.getLogStream(), serverTransport.getOutput());

        streamProcessorServiceFactory.createService(partition, name)
            .processor(taskInstanceStreamProcessor.createStreamProcessor(env))
            .processorId(TASK_QUEUE_STREAM_PROCESSOR_ID)
            .processorName("task-instance")
            .build();

        startExpireLockService(name, partition, env);
    }

    protected void startExpireLockService(ServiceName<Partition> partitionServiceName, Partition partition, TypedStreamEnvironment env)
    {
        final TaskExpireLockStreamProcessor expireLockStreamProcessor = new TaskExpireLockStreamProcessor();

        streamProcessorServiceFactory.createService(partition, partitionServiceName)
            .processor(expireLockStreamProcessor.createStreamProcessor(env))
            .processorId(TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID)
            .processorName("task-expire-lock")
            .build();
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        actorScheduler = serviceContext.getScheduler();
        streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
    }

    @Override
    public TaskQueueManagerService get()
    {
        return this;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public Injector<TaskSubscriptionManager> getTaskSubscriptionManagerInjector()
    {
        return taskSubscriptionManagerInjector;
    }

    public ServiceGroupReference<Partition> getPartitionsGroupReference()
    {
        return partitionsReference;
    }

    public void addPartition(ServiceName<Partition> name, Partition partition)
    {
        actorScheduler.submitActor(new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                startTaskQueue(name, partition);
            }
        });
    }

    public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector()
    {
        return streamProcessorServiceFactoryInjector;
    }

}
