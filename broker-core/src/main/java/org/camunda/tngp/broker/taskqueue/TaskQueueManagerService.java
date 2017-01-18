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
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.TASK_QUEUE_STREAM_PROCESSOR_ID;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueStreamProcessorServiceName;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.List;

import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.broker.taskqueue.processor.TaskInstanceStreamProcessor;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
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

public class TaskQueueManagerService implements Service<TaskQueueManager>, TaskQueueManager
{
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();

    protected final List<TaskQueueCfg> taskQueueCfgs;

    protected ServiceStartContext serviceContext;

    public TaskQueueManagerService(ConfigurationManager configurationManager)
    {
        taskQueueCfgs = configurationManager.readList("task-queue", TaskQueueCfg.class);
    }

    @Override
    public void startTaskQueue(TaskQueueCfg taskQueueCfg)
    {
        final String taskQueueName = taskQueueCfg.name;
        if (taskQueueName == null || taskQueueName.isEmpty())
        {

            throw new RuntimeException("Cannot start task queue " + taskQueueName + ": Configuration property 'name' cannot be null.");
        }

        final int taskQueueId = taskQueueCfg.id;
        if (taskQueueId < 0 || taskQueueId > Short.MAX_VALUE)
        {
            throw new RuntimeException("Cannot start task queue " + taskQueueName + ": Invalid value for task queue id " + taskQueueId +
                    ". Value must be in range [0," + Short.MAX_VALUE + "]");
        }

        final String logName = taskQueueCfg.logName;
        if (logName == null || logName.isEmpty())
        {
            throw new RuntimeException("Cannot start task queue " + taskQueueName + ": Mandatory configuration property 'logName' is not set.");
        }

        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueStreamProcessorServiceName(taskQueueName);
        final String streamProcessorName = streamProcessorServiceName.getName();

        String indexFile = taskQueueCfg.indexFile;
        if (taskQueueCfg.useTempIndexFile)
        {
            try
            {
                final File tempDir = Files.createTempDirectory("tngp-index-").toFile();
                final File tempFile = File.createTempFile(streamProcessorName + "-", ".idx", tempDir);
                System.out.format("Created temp file for task stream processor at location %s.\n", tempFile);
                indexFile = tempFile.getAbsolutePath();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Could not create temp file for task stream processor index", e);
            }
        }
        else if (indexFile == null || indexFile.isEmpty())
        {
            throw new RuntimeException(String.format("Cannot create task stream processor index, no index file name provided."));
        }

        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);

        final FileChannel indexFileChannel = FileUtil.openChannel(indexFile, true);
        final IndexStore indexStore = new FileChannelIndexStore(indexFileChannel);

        final CommandResponseWriter responseWriter = new CommandResponseWriter(sendBufferInjector.getValue());
        final TaskInstanceStreamProcessor streamProcessor = new TaskInstanceStreamProcessor(responseWriter, indexStore);

        final StreamProcessorService streamProcessorService = new StreamProcessorService(streamProcessorName, TASK_QUEUE_STREAM_PROCESSOR_ID, streamProcessor);
        serviceContext.createService(streamProcessorServiceName, streamProcessorService)
              .group(TASK_QUEUE_STREAM_PROCESSOR_SERVICE_GROUP_NAME)
              .dependency(logStreamServiceName, streamProcessorService.getSourceStreamInjector())
              .dependency(logStreamServiceName, streamProcessorService.getTargetStreamInjector())
              .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
              .dependency(AGENT_RUNNER_SERVICE, streamProcessorService.getAgentRunnerInjector())
              .install();
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
        // nothing to do
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
}