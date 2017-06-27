/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.task;

import static io.zeebe.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.logStreamServiceName;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_QUEUE_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.task.TaskQueueServiceNames.TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME;
import static io.zeebe.broker.task.TaskQueueServiceNames.taskQueueExpireLockStreamProcessorServiceName;
import static io.zeebe.broker.task.TaskQueueServiceNames.taskQueueInstanceStreamProcessorServiceName;

import java.time.Duration;

import io.zeebe.broker.logstreams.cfg.StreamProcessorCfg;
import io.zeebe.broker.logstreams.processor.StreamProcessorService;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.executor.ScheduledCommand;
import io.zeebe.broker.system.executor.ScheduledExecutor;
import io.zeebe.broker.task.processor.TaskExpireLockStreamProcessor;
import io.zeebe.broker.task.processor.TaskInstanceStreamProcessor;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.transport.clientapi.SubscribedEventWriter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

public class TaskQueueManagerService implements Service<TaskQueueManager>, TaskQueueManager, Actor
{
    protected static final String NAME = "task.queue.manager";

    protected final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    protected final Injector<ScheduledExecutor> executorInjector = new Injector<>();
    protected final Injector<TaskSubscriptionManager> taskSubscriptionManagerInjector = new Injector<>();
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> addStream(stream, name))
            .build();

    protected ServiceStartContext serviceContext;
    protected DeferredCommandContext asyncContext;
    protected StreamProcessorCfg streamProcessorCfg;

    protected ActorReference actorRef;

    protected ScheduledCommand scheduledCheckExpirationCmd;

    public TaskQueueManagerService(final ConfigurationManager configurationManager)
    {
        streamProcessorCfg = configurationManager.readEntry("index", StreamProcessorCfg.class);
    }

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
              .dependency(logStreamServiceName, taskInstanceStreamProcessorService.getSourceStreamInjector())
              .dependency(logStreamServiceName, taskInstanceStreamProcessorService.getTargetStreamInjector())
              .dependency(SNAPSHOT_STORAGE_SERVICE, taskInstanceStreamProcessorService.getSnapshotStorageInjector())
              .dependency(ACTOR_SCHEDULER_SERVICE, taskInstanceStreamProcessorService.getActorSchedulerInjector())
              .install();

        startExpireLockService(logName, logStreamServiceName);
    }

    protected void startExpireLockService(String logStreamName, ServiceName<LogStream> logStreamServiceName)
    {
        final ScheduledExecutor executor = executorInjector.getValue();

        final ServiceName<StreamProcessorController> expireLockStreamProcessorServiceName = taskQueueExpireLockStreamProcessorServiceName(logStreamName);
        final TaskExpireLockStreamProcessor expireLockStreamProcessor = new TaskExpireLockStreamProcessor();

        final StreamProcessorService expireLockStreamProcessorService = new StreamProcessorService(
                expireLockStreamProcessorServiceName.getName(),
                TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID,
                expireLockStreamProcessor)
                .eventFilter(TaskExpireLockStreamProcessor.eventFilter());

        serviceContext.createService(expireLockStreamProcessorServiceName, expireLockStreamProcessorService)
            .dependency(logStreamServiceName, expireLockStreamProcessorService.getSourceStreamInjector())
            .dependency(logStreamServiceName, expireLockStreamProcessorService.getTargetStreamInjector())
            .dependency(SNAPSHOT_STORAGE_SERVICE, expireLockStreamProcessorService.getSnapshotStorageInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, expireLockStreamProcessorService.getActorSchedulerInjector())
            .install()
            .thenRun(() ->
            {
                scheduledCheckExpirationCmd = executor.scheduleAtFixedRate(expireLockStreamProcessor::checkLockExpirationAsync, Duration.ofSeconds(30));
            });
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;
        this.asyncContext = new DeferredCommandContext();

        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        actorRef = actorScheduler.schedule(this);

    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        ctx.run(() ->
        {
            if (scheduledCheckExpirationCmd != null)
            {
                scheduledCheckExpirationCmd.cancel();
            }

            actorRef.close();
        });
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

    public Injector<ScheduledExecutor> getExecutorInjector()
    {
        return executorInjector;
    }

    public Injector<TaskSubscriptionManager> getTaskSubscriptionManagerInjector()
    {
        return taskSubscriptionManagerInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public void addStream(LogStream logStream, ServiceName<LogStream> logStreamServiceName)
    {
        asyncContext.runAsync((r) ->
        {
            startTaskQueue(logStream.getLogName());
        });
    }

    @Override
    public int doWork() throws Exception
    {
        return asyncContext.doWork();
    }

    @Override
    public String name()
    {
        return NAME;
    }

}
