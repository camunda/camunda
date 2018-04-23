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

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.deployment.data.*;
import io.zeebe.broker.system.deployment.handler.*;
import io.zeebe.broker.system.deployment.processor.*;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.broker.workflow.data.WorkflowState;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransport;

public class DeploymentManager implements Service<DeploymentManager>
{
    private final ServiceGroupReference<Partition> partitionsGroupReference = ServiceGroupReference.<Partition>create()
        .onAdd((name, partition) -> installDeploymentStreamProcessor(partition, name))
        .build();

    private final Injector<ClientTransport> managementClientInjector = new Injector<>();
    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector = new Injector<>();
    private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();

    private ClientTransport managementClient;
    private ServerTransport clientApiTransport;
    private TopologyManager topologyManager;
    private StreamProcessorServiceFactory streamProcessorServiceFactory;

    @Override
    public void start(ServiceStartContext startContext)
    {
        topologyManager = topologyManagerInjector.getValue();
        managementClient = managementClientInjector.getValue();
        clientApiTransport = clientApiTransportInjector.getValue();
        streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();
    }

    private void installDeploymentStreamProcessor(final Partition partition, ServiceName<Partition> partitionServiceName)
    {
        final WorkflowVersions workflowVersions = new WorkflowVersions();
        final PendingDeployments pendingDeployments = new PendingDeployments();
        final PendingWorkflows pendingWorkflows = new PendingWorkflows();

        final Duration deploymentRequestTimeout = Duration.ofSeconds(10);

        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(partition.getLogStream(), clientApiTransport.getOutput());

        final DeploymentEventWriter deploymentEventWriter = new DeploymentEventWriter(streamEnvironment);

        final RemoteWorkflowsManager remoteManager = new RemoteWorkflowsManager(pendingDeployments,
            pendingWorkflows,
            topologyManager,
            deploymentEventWriter,
            managementClient);

        final TypedStreamProcessor streamProcessor = createDeploymentStreamProcessor(workflowVersions,
            pendingDeployments,
            pendingWorkflows,
            deploymentRequestTimeout,
            streamEnvironment,
            deploymentEventWriter,
            remoteManager);

        streamProcessorServiceFactory.createService(partition, partitionServiceName)
            .processor(streamProcessor)
            .processorId(StreamProcessorIds.DEPLOYMENT_PROCESSOR_ID)
            .processorName("deployment")
            .build();
    }

    public static TypedStreamProcessor createDeploymentStreamProcessor(
        final WorkflowVersions workflowVersions,
        final PendingDeployments pendingDeployments,
        final PendingWorkflows pendingWorkflows,
        final Duration deploymentTimeout,
        final TypedStreamEnvironment streamEnvironment,
        final DeploymentEventWriter eventWriter,
        final RemoteWorkflowsManager remoteManager)
    {

        final TypedEventStreamProcessorBuilder streamProcessorBuilder = streamEnvironment.newStreamProcessor();

        final PartitionCollector partitionCollector = new PartitionCollector();
        partitionCollector.registerWith(streamProcessorBuilder);
        final TopicPartitions partitions = partitionCollector.getPartitions();

        final DeploymentTimer timer = new DeploymentTimer(pendingDeployments, eventWriter, deploymentTimeout);

        final TypedStreamProcessor streamProcessor = streamProcessorBuilder
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.CREATE, new DeploymentCreateProcessor(partitions, workflowVersions, pendingDeployments))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.VALIDATED, new DeploymentValidatedProcessor(pendingDeployments, timer))
            .onEvent(EventType.WORKFLOW_EVENT, WorkflowState.CREATE, new WorkflowCreateProcessor(partitions, pendingDeployments, pendingWorkflows, remoteManager))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.DISTRIBUTED, new DeploymentDistributedProcessor(pendingDeployments, pendingWorkflows, timer))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.TIMED_OUT, new DeploymentTimedOutProcessor(pendingDeployments, pendingWorkflows, timer, streamEnvironment.buildStreamReader()))
            .onEvent(EventType.WORKFLOW_EVENT, WorkflowState.DELETE, new WorkflowDeleteProcessor(pendingDeployments, pendingWorkflows, workflowVersions, remoteManager))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.REJECT, new DeploymentRejectProcessor(pendingDeployments))
            .withStateResource(workflowVersions.getRawMap())
            .withStateResource(pendingDeployments.getRawMap())
            .withStateResource(pendingWorkflows.getRawMap())
            .withListener(eventWriter)
            .withListener(timer)
            .withListener(remoteManager)
            .build();
        return streamProcessor;
    }

    @Override
    public DeploymentManager get()
    {
        return this;
    }

    public Injector<TopologyManager> getTopologyManagerInjector()
    {
        return topologyManagerInjector;
    }

    public Injector<ClientTransport> getManagementClientInjector()
    {
        return managementClientInjector;
    }
    public ServiceGroupReference<Partition> getPartitionsGroupReference()
    {
        return partitionsGroupReference;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector()
    {
        return streamProcessorServiceFactoryInjector;
    }


}
