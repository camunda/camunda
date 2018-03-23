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

import static io.zeebe.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_QUEUE_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.task.TaskQueueServiceNames.TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME;
import static io.zeebe.broker.task.TaskQueueServiceNames.taskQueueExpireLockStreamProcessorServiceName;
import static io.zeebe.broker.task.TaskQueueServiceNames.taskQueueInstanceStreamProcessorServiceName;

import java.time.Duration;

import io.zeebe.broker.logstreams.processor.StreamProcessorService;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.task.processor.TaskExpireLockStreamProcessor;
import io.zeebe.broker.task.processor.TaskInstanceStreamProcessor;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;

public class TaskQueueManagerService implements Service<TaskQueueManager>, TaskQueueManager
{
    protected static final String NAME = "task.queue.manager";
    public static final Duration LOCK_EXPIRATION_INTERVAL = Duration.ofSeconds(30);

    protected final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    protected final Injector<TaskSubscriptionManager> taskSubscriptionManagerInjector = new Injector<>();

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd(this::addStream)
            .build();

    private ServiceStartContext serviceContext;
    private ActorScheduler actorScheduler;

    @Override
    public void startTaskQueue(ServiceName<LogStream> logStreamServiceName, final LogStream stream)
    {
        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueInstanceStreamProcessorServiceName(stream.getLogName());
        final String streamProcessorName = streamProcessorServiceName.getName();

        final ServerTransport serverTransport = clientApiTransportInjector.getValue();

        final TaskSubscriptionManager taskSubscriptionManager = taskSubscriptionManagerInjector.getValue();

        final TaskInstanceStreamProcessor taskInstanceStreamProcessor = new TaskInstanceStreamProcessor(taskSubscriptionManager);
        final TypedStreamEnvironment env = new TypedStreamEnvironment(stream, serverTransport.getOutput());

        final TypedStreamProcessor streamProcessor = taskInstanceStreamProcessor.createStreamProcessor(env);
        final StreamProcessorService taskInstanceStreamProcessorService = new StreamProcessorService(
                streamProcessorName,
                TASK_QUEUE_STREAM_PROCESSOR_ID,
                streamProcessor)
                .eventFilter(streamProcessor.buildTypeFilter());

        serviceContext.createService(streamProcessorServiceName, taskInstanceStreamProcessorService)
              .group(TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME)
              .dependency(logStreamServiceName, taskInstanceStreamProcessorService.getLogStreamInjector())
              .dependency(SNAPSHOT_STORAGE_SERVICE, taskInstanceStreamProcessorService.getSnapshotStorageInjector())
              .install();

        startExpireLockService(logStreamServiceName, stream, env);
    }

    protected void startExpireLockService(ServiceName<LogStream> logStreamServiceName, LogStream stream, TypedStreamEnvironment env)
    {

        final ServiceName<StreamProcessorController> expireLockStreamProcessorServiceName = taskQueueExpireLockStreamProcessorServiceName(stream.getLogName());
        final TaskExpireLockStreamProcessor expireLockStreamProcessor = new TaskExpireLockStreamProcessor(env.buildStreamReader(), env.buildStreamWriter());
        final TypedStreamProcessor streamProcessor = expireLockStreamProcessor.createStreamProcessor(env);

        final StreamProcessorService expireLockStreamProcessorService = new StreamProcessorService(
                expireLockStreamProcessorServiceName.getName(),
                TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID,
                streamProcessor)
                .eventFilter(streamProcessor.buildTypeFilter());

        serviceContext.createService(expireLockStreamProcessorServiceName, expireLockStreamProcessorService)
            .dependency(logStreamServiceName, expireLockStreamProcessorService.getLogStreamInjector())
            .dependency(SNAPSHOT_STORAGE_SERVICE, expireLockStreamProcessorService.getSnapshotStorageInjector())
            .install();
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        actorScheduler = serviceContext.getScheduler();
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
    }

    @Override
    public TaskQueueManager get()
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

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

    public void addStream(ServiceName<LogStream> name, LogStream logStream)
    {
        actorScheduler.submitActor(new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                startTaskQueue(name, logStream);
            }
        });
    }

}
