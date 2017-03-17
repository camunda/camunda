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
package org.camunda.tngp.broker.workflow;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.logStreamServiceName;
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.DEPLOYMENT_PROCESSOR_ID;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.workflow.WorkflowQueueServiceNames.deploymentStreamProcessorServiceName;

import java.nio.channels.FileChannel;
import java.util.List;

import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.workflow.cfg.WorkflowQueueCfg;
import org.camunda.tngp.broker.workflow.processor.DeploymentStreamProcessor;
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

public class WorkflowQueueManagerService implements Service<WorkflowQueueManager>, WorkflowQueueManager
{
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();

    protected final List<WorkflowQueueCfg> queueConfigs;

    protected ServiceStartContext serviceContext;

    public WorkflowQueueManagerService(ConfigurationManager configurationManager)
    {
        this.queueConfigs = configurationManager.readList("workflow-queue", WorkflowQueueCfg.class);
    }

    @Override
    public void startWorkflowQueue(WorkflowQueueCfg config)
    {
        final String logName = config.logName;
        if (logName == null || logName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow queue: Mandatory configuration property 'logName' is not set.");
        }

        final ServiceName<StreamProcessorController> streamProcessorServiceName = deploymentStreamProcessorServiceName(logName);
        final String streamProcessorName = streamProcessorServiceName.getName();

        final IndexStore indexStore;

        final String indexFile = config.indexFile;
        if (config.useTempIndexFile)
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
            throw new RuntimeException(String.format("Cannot create deployment stream processor index, no index file name provided."));
        }

        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final CommandResponseWriter responseWriter = new CommandResponseWriter(sendBuffer);
        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);

        final DeploymentStreamProcessor deploymentStreamProcessor = new DeploymentStreamProcessor(responseWriter, indexStore);
        final StreamProcessorService deployemtStreamProcessorService = new StreamProcessorService(
                streamProcessorName,
                DEPLOYMENT_PROCESSOR_ID,
                deploymentStreamProcessor)
                .eventFilter(DeploymentStreamProcessor.eventFilter());

        serviceContext.createService(streamProcessorServiceName, deployemtStreamProcessorService)
              .dependency(logStreamServiceName, deployemtStreamProcessorService.getSourceStreamInjector())
              .dependency(logStreamServiceName, deployemtStreamProcessorService.getTargetStreamInjector())
              .dependency(SNAPSHOT_STORAGE_SERVICE, deployemtStreamProcessorService.getSnapshotStorageInjector())
              .dependency(AGENT_RUNNER_SERVICE, deployemtStreamProcessorService.getAgentRunnerInjector())
              .install();
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        serviceContext.run(() ->
        {
            for (WorkflowQueueCfg config : queueConfigs)
            {
                startWorkflowQueue(config);
            }
        });
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        // do nothing
    }

    @Override
    public WorkflowQueueManager get()
    {
        return this;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

}
