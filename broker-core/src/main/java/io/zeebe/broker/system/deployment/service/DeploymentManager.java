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
package io.zeebe.broker.system.deployment.service;

import java.time.Duration;

import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.logstreams.LogStreamServiceNames;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.SystemServiceNames;
import io.zeebe.broker.system.deployment.PendingDeploymentCheck;
import io.zeebe.broker.system.deployment.data.*;
import io.zeebe.broker.system.deployment.handler.CreateWorkflowRequestSender;
import io.zeebe.broker.system.deployment.processor.*;
import io.zeebe.broker.system.executor.ScheduledCommand;
import io.zeebe.broker.system.executor.ScheduledExecutor;
import io.zeebe.broker.system.log.PartitionState;
import io.zeebe.broker.system.log.TopicState;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.broker.workflow.data.WorkflowState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransport;

public class DeploymentManager implements Service<DeploymentManager>
{
    private final Injector<PartitionManager> partitionManagerInjector = new Injector<>();
    private final Injector<ClientTransport> managementClientInjector = new Injector<>();
    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<ScheduledExecutor> scheduledExecutorInjector = new Injector<>();

    private final Duration deploymentRequestTimeout = Duration.ofSeconds(10);

    private ServiceStartContext serviceContext;

    private PartitionManager partitionManager;
    private ClientTransport managementClient;
    private ServerTransport clientApiTransport;
    private ScheduledExecutor scheduledExecutor;

    private ScheduledCommand scheduledChecker;

    private final ServiceGroupReference<LogStream> systemStreamGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> installDeploymentStreamProcessor(stream, name))
            .build();

    @Override
    public void start(ServiceStartContext startContext)
    {
        serviceContext = startContext;

        partitionManager = partitionManagerInjector.getValue();
        managementClient = managementClientInjector.getValue();
        clientApiTransport = clientApiTransportInjector.getValue();
        scheduledExecutor = getScheduledExecutorInjector().getValue();
    }

    private void installDeploymentStreamProcessor(final LogStream logStream, ServiceName<LogStream> serviceName)
    {
        final TopicPartitions topicPartitions = new TopicPartitions();
        final WorkflowVersions workflowVersions = new WorkflowVersions();
        final PendingDeployments pendingDeployments = new PendingDeployments();
        final PendingWorkflows pendingWorkflows = new PendingWorkflows();

        final CreateWorkflowRequestSender workflowRequestSender = new CreateWorkflowRequestSender(partitionManager, managementClient);

        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(logStream, clientApiTransport.getOutput());

        final TypedStreamProcessor streamProcessor = streamEnvironment.newStreamProcessor()
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATED, new PartitionCreatedProcessor(topicPartitions))
            .onEvent(EventType.TOPIC_EVENT, TopicState.CREATED, new TopicCreatedProcessor(topicPartitions))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.CREATE_DEPLOYMENT, new DeploymentCreateProcessor(topicPartitions, workflowVersions, pendingDeployments, deploymentRequestTimeout))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.DEPLOYMENT_VALIDATED, new DeploymentValidatedProcessor(pendingDeployments))
            .onEvent(EventType.WORKFLOW_EVENT, WorkflowState.CREATE, new WorkflowCreateProcessor(topicPartitions, pendingDeployments, pendingWorkflows, workflowRequestSender))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.DEPLOYMENT_DISTRIBUTED, new DeploymentDistributedProcessor(pendingDeployments, pendingWorkflows))
            .withStateResource(topicPartitions.getRawMap())
            .withStateResource(workflowVersions.getRawMap())
            .withStateResource(pendingDeployments.getRawMap())
            .withStateResource(pendingWorkflows.getRawMap())
            .build();

        final StreamProcessorService streamProcessorService = new StreamProcessorService(
             "deployment",
             StreamProcessorIds.DEPLOYMENT_PROCESSOR_ID,
             streamProcessor)
             .eventFilter(streamProcessor.buildTypeFilter());

        serviceContext.createService(SystemServiceNames.DEPLOYMENT_PROCESSOR, streamProcessorService)
             .dependency(serviceName, streamProcessorService.getSourceStreamInjector())
             .dependency(serviceName, streamProcessorService.getTargetStreamInjector())
             .dependency(LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
             .dependency(SystemServiceNames.ACTOR_SCHEDULER_SERVICE, streamProcessorService.getActorSchedulerInjector())
             .install()
             .thenRun(() ->
             {
                 scheduledChecker = scheduleChecker(streamEnvironment, streamProcessor, workflowRequestSender, pendingDeployments, pendingWorkflows);
             });
    }

    private ScheduledCommand scheduleChecker(
            final TypedStreamEnvironment streamEnvironment,
            final TypedStreamProcessor streamProcessor,
            final CreateWorkflowRequestSender workflowRequestSender,
            final PendingDeployments pendingDeployments,
            final PendingWorkflows pendingWorkflows)
    {
        final PendingDeploymentCheck command = new PendingDeploymentCheck(
               workflowRequestSender,
               streamEnvironment.buildStreamReader(),
               streamEnvironment.buildStreamWriter(),
               pendingDeployments,
               pendingWorkflows);

        return scheduledExecutor.scheduleAtFixedRate(() -> streamProcessor.runAsync(command), Duration.ofMillis(250));
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        if (scheduledChecker != null)
        {
            scheduledChecker.cancel();
        }
    }

    @Override
    public DeploymentManager get()
    {
        return this;
    }

    public Injector<PartitionManager> getPartitionManagerInjector()
    {
        return partitionManagerInjector;
    }

    public Injector<ClientTransport> getManagementClientInjector()
    {
        return managementClientInjector;
    }
    public ServiceGroupReference<LogStream> getSystemStreamGroupReference()
    {
        return systemStreamGroupReference;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public Injector<ScheduledExecutor> getScheduledExecutorInjector()
    {
        return scheduledExecutorInjector;
    }

}
