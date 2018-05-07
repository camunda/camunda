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
package io.zeebe.broker.workflow;

import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.INCIDENT_PROCESSOR_ID;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.WORKFLOW_INSTANCE_PROCESSOR_ID;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.incident.processor.IncidentStreamProcessor;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.workflow.processor.WorkflowInstanceStreamProcessor;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransport;

/**
 * Tracks leader partitions and installs the workflow instance stream processors
 */
public class WorkflowStreamProcessingManagerService implements Service<WorkflowStreamProcessingManagerService>
{
    public static final int PAYLOAD_CACHE_SIZE = 64;

    protected static final String NAME = "workflow.queue.manager";

    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<ClientTransport> managementApiClientInjector = new Injector<>();
    private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();

    private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector = new Injector<>();

    private final ServiceGroupReference<Partition> partitionsGroupReference = ServiceGroupReference.<Partition>create()
        .onAdd((partitionName, partition) -> startStreamProcessors(partitionName, partition))
        .build();

    private StreamProcessorServiceFactory streamProcessorServiceFactory;

    private ServerTransport transport;
    private TopologyManager topologyManager;
    private ClientTransport managementApiClient;

    public void startStreamProcessors(ServiceName<Partition> partitionServiceName, Partition partition)
    {
        installWorkflowStreamProcessor(partition, partitionServiceName);
        installIncidentStreamProcessor(partition, partitionServiceName);
    }

    private void installWorkflowStreamProcessor(Partition partition, ServiceName<Partition> partitionServiceName)
    {
        final ServerTransport transport = clientApiTransportInjector.getValue();
        final CommandResponseWriter responseWriter = new CommandResponseWriter(transport.getOutput());

        final WorkflowInstanceStreamProcessor workflowInstanceStreamProcessor = new WorkflowInstanceStreamProcessor(responseWriter,
            managementApiClient,
            topologyManager,
            PAYLOAD_CACHE_SIZE);

        streamProcessorServiceFactory.createService(partition, partitionServiceName)
            .processor(workflowInstanceStreamProcessor)
            .processorId(WORKFLOW_INSTANCE_PROCESSOR_ID)
            .processorName("workflow-instance")
            .eventFilter(WorkflowInstanceStreamProcessor.eventFilter())
            .build();
    }

    private void installIncidentStreamProcessor(Partition partition, ServiceName<Partition> partitionServiceName)
    {
        final TypedStreamEnvironment env = new TypedStreamEnvironment(partition.getLogStream(), transport.getOutput());
        final IncidentStreamProcessor incidentProcessorFactory = new IncidentStreamProcessor();

        streamProcessorServiceFactory.createService(partition, partitionServiceName)
            .processor(incidentProcessorFactory.createStreamProcessor(env))
            .processorId(INCIDENT_PROCESSOR_ID)
            .processorName("incident")
            .build();
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.transport = clientApiTransportInjector.getValue();
        this.streamProcessorServiceFactory =  streamProcessorServiceFactoryInjector.getValue();
        this.topologyManager = topologyManagerInjector.getValue();
        this.managementApiClient = managementApiClientInjector.getValue();
    }

    @Override
    public WorkflowStreamProcessingManagerService get()
    {
        return this;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public ServiceGroupReference<Partition> getPartitionsGroupReference()
    {
        return partitionsGroupReference;
    }

    public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector()
    {
        return streamProcessorServiceFactoryInjector;
    }

    public Injector<ClientTransport> getManagementApiClientInjector()
    {
        return managementApiClientInjector;
    }

    public Injector<TopologyManager> getTopologyManagerInjector()
    {
        return topologyManagerInjector;
    }
}
