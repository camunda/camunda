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
package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.*;
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.*;
import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.*;

import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.List;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.executor.ScheduledCommand;
import org.camunda.tngp.broker.system.executor.ScheduledExecutor;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.broker.taskqueue.processor.TaskExpireLockStreamProcessor;
import org.camunda.tngp.broker.taskqueue.processor.TaskInstanceStreamProcessor;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.SingleMessageWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.FileUtil;

public class TaskQueueManagerService implements Service<TaskQueueManager>, TaskQueueManager, Agent
{
    protected static final String NAME = "task.queue.manager";

    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final Injector<ScheduledExecutor> executorInjector = new Injector<>();
    protected final Injector<TaskSubscriptionManager> taskSubscriptionManagerInjector = new Injector<>();
    protected final Injector<AgentRunnerServices> agentRunnerServicesInjector = new Injector<>();

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> addStream(stream, name))
            .build();

    protected final List<TaskQueueCfg> taskQueueCfgs;

    protected ServiceStartContext serviceContext;
    protected DeferredCommandContext asyncContext;

    protected ScheduledCommand scheduledCheckExpirationCmd;

    public TaskQueueManagerService(ConfigurationManager configurationManager)
    {
        taskQueueCfgs = configurationManager.readList("task-queue", TaskQueueCfg.class);
    }

    @Override
    public void startTaskQueue(TaskQueueCfg taskQueueCfg)
    {
        final String logName = taskQueueCfg.logName;
        if (logName == null || logName.isEmpty())
        {
            throw new RuntimeException("Cannot start task queue: Mandatory configuration property 'logName' is not set.");
        }

        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueInstanceStreamProcessorServiceName(logName);
        final String streamProcessorName = streamProcessorServiceName.getName();

        final IndexStore indexStore;

        final String indexFile = taskQueueCfg.indexFile;
        if (taskQueueCfg.useTempIndexFile)
        {
            indexStore = FileChannelIndexStore.tempFileIndexStore();
        }
        else if (indexFile != null && !indexFile.isEmpty())
        {
            final FileChannel indexFileChannel = FileUtil.openChannel(indexFile, true);
            indexStore = new FileChannelIndexStore(indexFileChannel);
        }
        else
        {
            throw new RuntimeException(String.format("Cannot create task stream processor index, no index file name provided."));
        }

        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final CommandResponseWriter responseWriter = new CommandResponseWriter(sendBuffer);
        final SubscribedEventWriter subscribedEventWriter = new SubscribedEventWriter(new SingleMessageWriter(sendBuffer));
        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);
        final TaskSubscriptionManager taskSubscriptionManager = taskSubscriptionManagerInjector.getValue();

        final TaskInstanceStreamProcessor taskInstanceStreamProcessor = new TaskInstanceStreamProcessor(responseWriter, subscribedEventWriter, indexStore, taskSubscriptionManager);
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
              .dependency(AGENT_RUNNER_SERVICE, taskInstanceStreamProcessorService.getAgentRunnerInjector())
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
            .dependency(AGENT_RUNNER_SERVICE, expireLockStreamProcessorService.getAgentRunnerInjector())
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

        final AgentRunnerServices agentRunnerService = agentRunnerServicesInjector.getValue();
        agentRunnerService.conductorAgentRunnerService().run(this);

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

            final AgentRunnerServices agentRunnerService = agentRunnerServicesInjector.getValue();
            agentRunnerService.conductorAgentRunnerService().remove(this);
        });
    }

    @Override
    public TaskQueueManager get()
    {
        return this;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
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

    public Injector<AgentRunnerServices> getAgentRunnerServicesInjector()
    {
        return agentRunnerServicesInjector;
    }

    public void addStream(LogStream logStream, ServiceName<LogStream> logStreamServiceName)
    {
        asyncContext.runAsync((r) ->
        {
            for (int i = 0; i < taskQueueCfgs.size(); i++)
            {
                final String logName = logStream.getLogName();
                final TaskQueueCfg cfg = taskQueueCfgs.get(i);
                if (logName.equals(cfg.logName))
                {
                    startTaskQueue(cfg);
                    break;
                }
            }
        });
    }

    @Override
    public int doWork() throws Exception
    {
        return asyncContext.doWork();
    }

    @Override
    public String roleName()
    {
        return NAME;
    }

}