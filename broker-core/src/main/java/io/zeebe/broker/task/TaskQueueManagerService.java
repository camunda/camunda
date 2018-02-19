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
import static io.zeebe.broker.logstreams.LogStreamServiceNames.logStreamServiceName;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_QUEUE_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.task.TaskQueueServiceNames.*;

import java.time.Duration;

import io.zeebe.broker.logstreams.processor.StreamProcessorService;
import io.zeebe.broker.task.processor.TaskExpireLockStreamProcessor;
import io.zeebe.broker.task.processor.TaskInstanceStreamProcessor;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.transport.clientapi.SubscribedEventWriter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.ZbActorScheduler;

public class TaskQueueManagerService implements Service<TaskQueueManager>, TaskQueueManager
{
    protected static final String NAME = "task.queue.manager";
    public static final Duration LOCK_EXPIRATION_INTERVAL = Duration.ofSeconds(30);

    protected final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    protected final Injector<TaskSubscriptionManager> taskSubscriptionManagerInjector = new Injector<>();
    protected final Injector<ZbActorScheduler> actorSchedulerInjector = new Injector<>();

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> addStream(stream))
            .build();

    private ServiceStartContext serviceContext;
    private ZbActorScheduler actorScheduler;

    @Override
    public void startTaskQueue(final String logName)
    {
        if (logName == null || logName.isEmpty())
        {
            throw new RuntimeException("Cannot start task queue: Mandatory configuration property 'logName' is not set.");
        }

        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueInstanceStreamProcessorServiceName(logName);
        final String streamProcessorName = streamProcessorServiceName.getName();

        final ServerTransport serverTransport = clientApiTransportInjector.getValue();

        final CommandResponseWriter responseWriter = new CommandResponseWriter(serverTransport.getOutput());
        final SubscribedEventWriter subscribedEventWriter = new SubscribedEventWriter(serverTransport.getOutput());
        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);
        final TaskSubscriptionManager taskSubscriptionManager = taskSubscriptionManagerInjector.getValue();

        final TaskInstanceStreamProcessor taskInstanceStreamProcessor = new TaskInstanceStreamProcessor(responseWriter, subscribedEventWriter, taskSubscriptionManager);
        final StreamProcessorService taskInstanceStreamProcessorService = new StreamProcessorService(
                streamProcessorName,
                TASK_QUEUE_STREAM_PROCESSOR_ID,
                taskInstanceStreamProcessor)
                .eventFilter(TaskInstanceStreamProcessor.eventFilter());

        serviceContext.createService(streamProcessorServiceName, taskInstanceStreamProcessorService)
              .group(TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME)
              .dependency(logStreamServiceName, taskInstanceStreamProcessorService.getLogStreamInjector())
              .dependency(SNAPSHOT_STORAGE_SERVICE, taskInstanceStreamProcessorService.getSnapshotStorageInjector())
              .dependency(ACTOR_SCHEDULER_SERVICE, taskInstanceStreamProcessorService.getActorSchedulerInjector())
              .install();

        startExpireLockService(logName, logStreamServiceName);
    }

    protected void startExpireLockService(String logStreamName, ServiceName<LogStream> logStreamServiceName)
    {

        final ServiceName<StreamProcessorController> expireLockStreamProcessorServiceName = taskQueueExpireLockStreamProcessorServiceName(logStreamName);
        final TaskExpireLockStreamProcessor expireLockStreamProcessor = new TaskExpireLockStreamProcessor();

        final StreamProcessorService expireLockStreamProcessorService = new StreamProcessorService(
                expireLockStreamProcessorServiceName.getName(),
                TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID,
                expireLockStreamProcessor)
                .eventFilter(TaskExpireLockStreamProcessor.eventFilter());

        serviceContext.createService(expireLockStreamProcessorServiceName, expireLockStreamProcessorService)
            .dependency(logStreamServiceName, expireLockStreamProcessorService.getLogStreamInjector())
            .dependency(SNAPSHOT_STORAGE_SERVICE, expireLockStreamProcessorService.getSnapshotStorageInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, expireLockStreamProcessorService.getActorSchedulerInjector())
            .install();
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        actorScheduler = actorSchedulerInjector.getValue();
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

    public Injector<ZbActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public void addStream(LogStream logStream)
    {
        actorScheduler.submitActor(new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                startTaskQueue(logStream.getLogName());
            }
        });
    }

}
