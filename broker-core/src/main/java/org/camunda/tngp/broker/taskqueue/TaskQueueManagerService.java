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

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.logStreamServiceName;
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.TASK_EXPIRE_LOCK_STREAM_PROCESSOR_ID;
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.TASK_QUEUE_STREAM_PROCESSOR_ID;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueExpireLockStreamProcessorServiceName;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueInstanceStreamProcessorServiceName;

import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.List;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.system.ConfigurationManager;
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
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.FileUtil;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.time.ClockUtil;

public class TaskQueueManagerService implements Service<TaskQueueManager>, TaskQueueManager
{
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final Injector<AgentRunnerServices> agentRunnerServicesInjector = new Injector<>();

    protected final List<TaskQueueCfg> taskQueueCfgs;

    protected ServiceStartContext serviceContext;

    protected CheckExpireLockScheduler checkExpireLockScheduler;

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
            indexStore = FileChannelIndexStore.tempFileIndexStore(streamProcessorName);
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

        final TaskInstanceStreamProcessor taskInstanceStreamProcessor = new TaskInstanceStreamProcessor(responseWriter, subscribedEventWriter, indexStore);
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
        final AgentRunnerService agentRunnerService = agentRunnerServicesInjector.getValue().conductorAgentRunnerService();

        final ServiceName<StreamProcessorController> expireLockStreamProcessorServiceName = taskQueueExpireLockStreamProcessorServiceName(logStreamName);
        final TaskExpireLockStreamProcessor expireLockStreamProcessor = new TaskExpireLockStreamProcessor();

        final String schedulerName = String.format("taskqueue.%s.expire.lock.scheduler", logStreamName);
        checkExpireLockScheduler = new CheckExpireLockScheduler(agentRunnerService, schedulerName, expireLockStreamProcessor, Duration.ofSeconds(30));

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
            .thenRun(() -> checkExpireLockScheduler.start());
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        serviceContext.run(() ->
        {
            for (TaskQueueCfg taskQueueCfg : taskQueueCfgs)
            {
                startTaskQueue(taskQueueCfg);
            }
        });
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        ctx.run(() -> checkExpireLockScheduler.stop());
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

    public Injector<AgentRunnerServices> getAgentRunnerServicesInjector()
    {
        return agentRunnerServicesInjector;
    }

    class CheckExpireLockScheduler implements Agent
    {
        protected final AgentRunnerService agentRunnerService;
        protected final String name;
        protected final TaskExpireLockStreamProcessor streamProcessor;
        protected final long period;

        protected long dueDate;

        CheckExpireLockScheduler(AgentRunnerService agentRunnerService, String name, TaskExpireLockStreamProcessor streamProcessor, Duration period)
        {
            this.agentRunnerService = agentRunnerService;
            this.name = name;
            this.streamProcessor = streamProcessor;
            this.period = period.toMillis();

            dueDate = getNextDueDate();
        }

        public void start()
        {
            agentRunnerService.run(this);
        }

        public void stop()
        {
            agentRunnerService.remove(this);
        }

        @Override
        public int doWork() throws Exception
        {
            int workCount = 0;

            if (ClockUtil.getCurrentTimeInMillis() >= dueDate)
            {
                streamProcessor.checkLockExpirationAsync();

                dueDate = getNextDueDate();

                workCount += 1;
            }

            return workCount;
        }

        protected long getNextDueDate()
        {
            return ClockUtil.getCurrentTimeInMillis() + this.period;
        }

        @Override
        public String roleName()
        {
            return name;
        }

    }

}