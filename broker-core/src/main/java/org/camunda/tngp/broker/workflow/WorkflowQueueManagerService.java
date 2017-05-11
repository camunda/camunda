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
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.INCIDENT_PROCESSOR_ID;
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.WORKFLOW_INSTANCE_PROCESSOR_ID;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.workflow.WorkflowQueueServiceNames.deploymentStreamProcessorServiceName;
import static org.camunda.tngp.broker.workflow.WorkflowQueueServiceNames.incidentStreamProcessorServiceName;
import static org.camunda.tngp.broker.workflow.WorkflowQueueServiceNames.workflowInstanceStreamProcessorServiceName;

import java.io.File;
import java.nio.channels.FileChannel;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.incident.IncidentStreamProcessorErrorHandler;
import org.camunda.tngp.broker.incident.processor.IncidentStreamProcessor;
import org.camunda.tngp.broker.logstreams.cfg.LogStreamsCfg;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.workflow.processor.DeploymentStreamProcessor;
import org.camunda.tngp.broker.workflow.processor.WorkflowInstanceStreamProcessor;
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
import org.camunda.tngp.util.EnsureUtil;
import org.camunda.tngp.util.FileUtil;

public class WorkflowQueueManagerService implements Service<WorkflowQueueManager>, WorkflowQueueManager, Agent
{
    protected static final String NAME = "workflow.queue.manager";

    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final Injector<AgentRunnerServices> agentRunnerServicesInjector = new Injector<>();

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> addStream(stream, name))
            .build();

    protected ServiceStartContext serviceContext;
    protected DeferredCommandContext asyncContext;
    protected LogStreamsCfg logStreamsCfg;
    protected WorkflowCfg workflowCfg;

    public WorkflowQueueManagerService(final ConfigurationManager configurationManager)
    {
        logStreamsCfg = configurationManager.readEntry("logs", LogStreamsCfg.class);
        workflowCfg = configurationManager.readEntry("workflow", WorkflowCfg.class);
    }

    @Override
    public void startWorkflowQueue(final LogStream logStream)
    {
        EnsureUtil.ensureNotNull("logStream", logStream);

        installDeploymentStreamProcessor(logStream.getLogName());
        installWorkflowStreamProcessor(logStream);
        installIncidentStreamProcessor(logStream);
    }

    private void installDeploymentStreamProcessor(final String logName)
    {
        final ServiceName<StreamProcessorController> streamProcessorServiceName = deploymentStreamProcessorServiceName(logName);
        final String streamProcessorName = streamProcessorServiceName.getName();

        final IndexStore indexStore = createIndexStore("workflow.deployment");

        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final CommandResponseWriter responseWriter = new CommandResponseWriter(sendBuffer);
        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);

        final DeploymentStreamProcessor deploymentStreamProcessor = new DeploymentStreamProcessor(responseWriter, indexStore);
        final StreamProcessorService deploymentStreamProcessorService = new StreamProcessorService(
                streamProcessorName,
                DEPLOYMENT_PROCESSOR_ID,
                deploymentStreamProcessor)
                .eventFilter(DeploymentStreamProcessor.eventFilter());

        serviceContext.createService(streamProcessorServiceName, deploymentStreamProcessorService)
                .dependency(logStreamServiceName, deploymentStreamProcessorService.getSourceStreamInjector())
                .dependency(logStreamServiceName, deploymentStreamProcessorService.getTargetStreamInjector())
                .dependency(SNAPSHOT_STORAGE_SERVICE, deploymentStreamProcessorService.getSnapshotStorageInjector())
                .dependency(AGENT_RUNNER_SERVICE, deploymentStreamProcessorService.getAgentRunnerInjector())
                .install();
    }

    private void installWorkflowStreamProcessor(final LogStream logStream)
    {
        final ServiceName<StreamProcessorController> streamProcessorServiceName = workflowInstanceStreamProcessorServiceName(logStream.getLogName());
        final String streamProcessorName = streamProcessorServiceName.getName();

        final IndexStore workflowPositionIndexStore = createIndexStore("workflow.instance.position");
        final IndexStore workflowVersionIndexStore = createIndexStore("workflow.instance.version");
        final IndexStore workflowInstanceIndexStore = createIndexStore("workflow.instance");
        final IndexStore activityInstanceIndexStore = createIndexStore("workflow.activity");
        final IndexStore workflowInstancePayloadIndexStore = createIndexStore("workflow.instance.payload");

        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final CommandResponseWriter responseWriter = new CommandResponseWriter(sendBuffer);
        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logStream.getLogName());

        // incident target log stream could be configurable
        final IncidentStreamProcessorErrorHandler errorHandler = new IncidentStreamProcessorErrorHandler(logStream, WORKFLOW_INSTANCE_PROCESSOR_ID);

        final WorkflowInstanceStreamProcessor workflowInstanceStreamProcessor = new WorkflowInstanceStreamProcessor(
                responseWriter,
                workflowPositionIndexStore,
                workflowVersionIndexStore,
                workflowInstanceIndexStore,
                activityInstanceIndexStore,
                workflowInstancePayloadIndexStore,
                workflowCfg.cacheSize,
                workflowCfg.maxPayloadSize);

        final StreamProcessorService workflowStreamProcessorService = new StreamProcessorService(
                streamProcessorName,
                WORKFLOW_INSTANCE_PROCESSOR_ID,
                workflowInstanceStreamProcessor)
                .eventFilter(WorkflowInstanceStreamProcessor.eventFilter())
                .errorHandler(errorHandler);

        serviceContext.createService(streamProcessorServiceName, workflowStreamProcessorService)
                .dependency(logStreamServiceName, workflowStreamProcessorService.getSourceStreamInjector())
                .dependency(logStreamServiceName, workflowStreamProcessorService.getTargetStreamInjector())
                .dependency(SNAPSHOT_STORAGE_SERVICE, workflowStreamProcessorService.getSnapshotStorageInjector())
                .dependency(AGENT_RUNNER_SERVICE, workflowStreamProcessorService.getAgentRunnerInjector())
                .install();
    }

    private void installIncidentStreamProcessor(final LogStream logStream)
    {
        final ServiceName<StreamProcessorController> streamProcessorServiceName = incidentStreamProcessorServiceName(logStream.getLogName());
        final String streamProcessorName = streamProcessorServiceName.getName();

        final IndexStore incidentInstanceIndex = createIndexStore("incident.instance");
        final IndexStore activityInstanceIndex = createIndexStore("incident.activity");
        final IndexStore incidentTaskIndex = createIndexStore("incident.task");

        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logStream.getLogName());

        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final CommandResponseWriter responseWriter = new CommandResponseWriter(sendBuffer);

        final IncidentStreamProcessor incidentStreamProcessor = new IncidentStreamProcessor(responseWriter, incidentInstanceIndex, activityInstanceIndex, incidentTaskIndex);

        final StreamProcessorService incidentStreamProcessorService = new StreamProcessorService(
                streamProcessorName,
                INCIDENT_PROCESSOR_ID,
                incidentStreamProcessor)
                .eventFilter(IncidentStreamProcessor.eventFilter());

        serviceContext.createService(streamProcessorServiceName, incidentStreamProcessorService)
                .dependency(logStreamServiceName, incidentStreamProcessorService.getSourceStreamInjector())
                .dependency(logStreamServiceName, incidentStreamProcessorService.getTargetStreamInjector())
                .dependency(SNAPSHOT_STORAGE_SERVICE, incidentStreamProcessorService.getSnapshotStorageInjector())
                .dependency(AGENT_RUNNER_SERVICE, incidentStreamProcessorService.getAgentRunnerInjector())
                .install();
    }

    private IndexStore createIndexStore(final String indexName)
    {
        final IndexStore indexStore;


        final String indexDirectory = logStreamsCfg.indexDirectory;
        if (indexDirectory != null && !indexDirectory.isEmpty())
        {
            final String indexFile = indexDirectory + File.separator + indexName + ".idx";
            final FileChannel indexFileChannel = FileUtil.openChannel(indexFile, true);
            indexStore = new FileChannelIndexStore(indexFileChannel);
        }
        else
        {
            throw new RuntimeException("Cannot create stream processor index, no index file name provided.");
        }

        return indexStore;
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
            final AgentRunnerServices agentRunnerService = agentRunnerServicesInjector.getValue();
            agentRunnerService.conductorAgentRunnerService().remove(this);
        });
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
            startWorkflowQueue(logStream);
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
