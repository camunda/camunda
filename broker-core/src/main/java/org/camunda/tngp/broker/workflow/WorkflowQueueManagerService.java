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
import static org.camunda.tngp.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static org.camunda.tngp.broker.workflow.WorkflowQueueServiceNames.deploymentStreamProcessorServiceName;
import static org.camunda.tngp.broker.workflow.WorkflowQueueServiceNames.incidentStreamProcessorServiceName;
import static org.camunda.tngp.broker.workflow.WorkflowQueueServiceNames.workflowInstanceStreamProcessorServiceName;

import java.nio.channels.FileChannel;

import org.camunda.tngp.broker.incident.IncidentStreamProcessorErrorHandler;
import org.camunda.tngp.broker.incident.processor.IncidentStreamProcessor;
import org.camunda.tngp.broker.logstreams.cfg.StreamProcessorCfg;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.workflow.processor.DeploymentStreamProcessor;
import org.camunda.tngp.broker.workflow.processor.WorkflowInstanceStreamProcessor;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.*;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.EnsureUtil;
import org.camunda.tngp.util.FileUtil;
import org.camunda.tngp.util.actor.Actor;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;

public class WorkflowQueueManagerService implements Service<WorkflowQueueManager>, WorkflowQueueManager, Actor
{
    protected static final String NAME = "workflow.queue.manager";

    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> addStream(stream, name))
            .build();

    protected ServiceStartContext serviceContext;
    protected DeferredCommandContext asyncContext;
    protected StreamProcessorCfg streamProcessorCfg;
    protected WorkflowCfg workflowCfg;
    protected IndexStore workflowDeploymentIndexStore;
    protected IndexStore workflowPositionIndexStore;
    protected IndexStore workflowVersionIndexStore;
    protected IndexStore workflowInstanceIndexStore;
    protected IndexStore activityInstanceIndexStore;
    protected IndexStore workflowInstancePayloadIndexStore;
    protected IndexStore incidentInstanceIndex;
    protected IndexStore activityInstanceIndex;
    protected IndexStore incidentTaskIndex;

    protected ActorReference actorRef;

    public WorkflowQueueManagerService(final ConfigurationManager configurationManager)
    {
        streamProcessorCfg = configurationManager.readEntry("index", StreamProcessorCfg.class);
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

        this.workflowDeploymentIndexStore = createIndexStore("workflow.deployment");

        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final CommandResponseWriter responseWriter = new CommandResponseWriter(sendBuffer);
        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);

        final DeploymentStreamProcessor deploymentStreamProcessor = new DeploymentStreamProcessor(responseWriter, workflowDeploymentIndexStore);
        final StreamProcessorService deploymentStreamProcessorService = new StreamProcessorService(
                streamProcessorName,
                DEPLOYMENT_PROCESSOR_ID,
                deploymentStreamProcessor)
                .eventFilter(DeploymentStreamProcessor.eventFilter());

        serviceContext.createService(streamProcessorServiceName, deploymentStreamProcessorService)
                .dependency(logStreamServiceName, deploymentStreamProcessorService.getSourceStreamInjector())
                .dependency(logStreamServiceName, deploymentStreamProcessorService.getTargetStreamInjector())
                .dependency(SNAPSHOT_STORAGE_SERVICE, deploymentStreamProcessorService.getSnapshotStorageInjector())
                .dependency(ACTOR_SCHEDULER_SERVICE, deploymentStreamProcessorService.getActorSchedulerInjector())
                .install();
    }

    private void installWorkflowStreamProcessor(final LogStream logStream)
    {
        final ServiceName<StreamProcessorController> streamProcessorServiceName = workflowInstanceStreamProcessorServiceName(logStream.getLogName());
        final String streamProcessorName = streamProcessorServiceName.getName();

        workflowPositionIndexStore = createIndexStore("workflow.instance.position");
        workflowVersionIndexStore = createIndexStore("workflow.instance.version");
        workflowInstanceIndexStore = createIndexStore("workflow.instance");
        activityInstanceIndexStore = createIndexStore("workflow.activity");
        workflowInstancePayloadIndexStore = createIndexStore("workflow.instance.payload");

        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final CommandResponseWriter responseWriter = new CommandResponseWriter(sendBuffer);
        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logStream.getLogName());

        final IncidentStreamProcessorErrorHandler errorHandler = new IncidentStreamProcessorErrorHandler(logStream);

        final WorkflowInstanceStreamProcessor workflowInstanceStreamProcessor = new WorkflowInstanceStreamProcessor(
                responseWriter,
                workflowPositionIndexStore,
                workflowVersionIndexStore,
                workflowInstanceIndexStore,
                activityInstanceIndexStore,
                workflowInstancePayloadIndexStore,
                workflowCfg.deploymentCacheSize,
                workflowCfg.payloadCacheSize);

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
                .dependency(ACTOR_SCHEDULER_SERVICE, workflowStreamProcessorService.getActorSchedulerInjector())
                .install();
    }

    private void installIncidentStreamProcessor(final LogStream logStream)
    {
        final ServiceName<StreamProcessorController> streamProcessorServiceName = incidentStreamProcessorServiceName(logStream.getLogName());
        final String streamProcessorName = streamProcessorServiceName.getName();


        incidentInstanceIndex = createIndexStore("incident.instance");
        activityInstanceIndex = createIndexStore("incident.activity");
        incidentTaskIndex = createIndexStore("incident.task");


        final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logStream.getLogName());

        final IncidentStreamProcessor incidentStreamProcessor = new IncidentStreamProcessor(incidentInstanceIndex, activityInstanceIndex, incidentTaskIndex);

        final StreamProcessorService incidentStreamProcessorService = new StreamProcessorService(
                streamProcessorName,
                INCIDENT_PROCESSOR_ID,
                incidentStreamProcessor)
                .eventFilter(IncidentStreamProcessor.eventFilter());

        serviceContext.createService(streamProcessorServiceName, incidentStreamProcessorService)
                .dependency(logStreamServiceName, incidentStreamProcessorService.getSourceStreamInjector())
                .dependency(logStreamServiceName, incidentStreamProcessorService.getTargetStreamInjector())
                .dependency(SNAPSHOT_STORAGE_SERVICE, incidentStreamProcessorService.getSnapshotStorageInjector())
                .dependency(ACTOR_SCHEDULER_SERVICE, incidentStreamProcessorService.getActorSchedulerInjector())
                .install();
    }

    private IndexStore createIndexStore(final String indexName)
    {
        final IndexStore indexStore;


        final String indexDirectory = streamProcessorCfg.directory;
        if (indexDirectory != null && !indexDirectory.isEmpty())
        {
            final String indexFile = indexDirectory + indexName + ".idx";
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

        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        actorRef = actorScheduler.schedule(this);
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        ctx.run(() ->
        {
            actorRef.close();

            clear();
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

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public void addStream(LogStream logStream, ServiceName<LogStream> logStreamServiceName)
    {
        asyncContext.runAsync((r) ->
        {
            startWorkflowQueue(logStream);
        });
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
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

    private void clear()
    {
        workflowDeploymentIndexStore.flush();
        workflowPositionIndexStore.flush();
        workflowVersionIndexStore.flush();
        workflowInstanceIndexStore.flush();
        activityInstanceIndexStore.flush();
        workflowInstancePayloadIndexStore.flush();
        incidentInstanceIndex.flush();
        activityInstanceIndex.flush();
        incidentTaskIndex.flush();
        workflowDeploymentIndexStore.close();
        workflowPositionIndexStore.close();
        workflowVersionIndexStore.close();
        workflowInstanceIndexStore.close();
        activityInstanceIndexStore.close();
        workflowInstancePayloadIndexStore.close();
        incidentInstanceIndex.close();
        activityInstanceIndex.close();
        incidentTaskIndex.close();
    }

}
