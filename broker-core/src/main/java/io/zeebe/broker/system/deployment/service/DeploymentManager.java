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

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.orchestration.topic.TopicState;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.deployment.data.*;
import io.zeebe.broker.system.deployment.processor.*;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ServerTransport;

public class DeploymentManager implements Service<DeploymentManager>
{
    private final ServiceGroupReference<Partition> partitionsGroupReference = ServiceGroupReference.<Partition>create()
        .onAdd((name, partition) -> installServices(partition, name))
        .build();

    private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector = new Injector<>();
    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<DeploymentManagerRequestHandler> requestHandlerServiceInjector = new Injector<>();

    private ServerTransport clientApiTransport;
    private StreamProcessorServiceFactory streamProcessorServiceFactory;

    private DeploymentManagerRequestHandler requestHandlerService;

    @Override
    public void start(ServiceStartContext startContext)
    {
        this.clientApiTransport = clientApiTransportInjector.getValue();
        this.streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();
        requestHandlerService = requestHandlerServiceInjector.getValue();
    }

    private void installServices(final Partition partition, ServiceName<Partition> partitionServiceName)
    {
        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(partition.getLogStream(), clientApiTransport.getOutput());

        final DeploymentPositionByWorkflowKey deploymentPositionByWorkflowKey = new DeploymentPositionByWorkflowKey();
        final LastWorkflowKey lastWorkflowKey = new LastWorkflowKey();
        final LatestVersionByProcessIdAndTopicName latestVersionByProcessIdAndTopicName = new LatestVersionByProcessIdAndTopicName();
        final TopicNames topicNames = new TopicNames();
        final WorkflowKeyByProcessIdAndVersion workflowKeyByProcessIdAndVersion = new WorkflowKeyByProcessIdAndVersion();

        final TypedStreamProcessor streamProcessor = streamEnvironment.newStreamProcessor()
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.CREATE, new DeploymentCreateEventProcessor(latestVersionByProcessIdAndTopicName, lastWorkflowKey, topicNames))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.CREATED, new DeploymentCreatedEventProcess(deploymentPositionByWorkflowKey, workflowKeyByProcessIdAndVersion))
            .onEvent(EventType.DEPLOYMENT_EVENT, DeploymentState.REJECTED, new DeploymentRejectedEventProcessor())
            .onEvent(EventType.TOPIC_EVENT, TopicState.CREATING, new DeploymentTopicCreatingEventProcessor(topicNames))
            .withStateResource(lastWorkflowKey.getRawValue())
            .withStateResource(latestVersionByProcessIdAndTopicName.getRawMap())
            .withStateResource(topicNames.getRawMap())
            .withStateResource(workflowKeyByProcessIdAndVersion.getRawMap())
            .withStateResource(deploymentPositionByWorkflowKey.getRawMap())
            .withListener(new StreamProcessorLifecycleAware()
            {
                private BufferedLogStreamReader reader;

                @Override
                public void onOpen(TypedStreamProcessor streamProcessor)
                {
                    final StreamProcessorContext ctx = streamProcessor.getStreamProcessorContext();

                    reader = new BufferedLogStreamReader();
                    reader.wrap(ctx.getLogStream());

                    final DeploymentWorkflowsCache cache = new DeploymentWorkflowsCache(reader, deploymentPositionByWorkflowKey);

                    final FetchWorkflowRequestHandler requestHandler = new FetchWorkflowRequestHandler(streamProcessor.getActor(),
                        workflowKeyByProcessIdAndVersion,
                        latestVersionByProcessIdAndTopicName,
                        cache);

                    requestHandlerService.setFetchWorkflowRequestHandler(requestHandler);
                }

                @Override
                public void onClose()
                {
                    requestHandlerService.setFetchWorkflowRequestHandler(null);
                    reader.close();
                }
            })
            .build();

        streamProcessorServiceFactory.createService(partition, partitionServiceName)
            .processor(streamProcessor)
            .processorId(StreamProcessorIds.DEPLOYMENT_PROCESSOR_ID)
            .processorName("deployment")
            .build();
    }

    @Override
    public DeploymentManager get()
    {
        return this;
    }

    public ServiceGroupReference<Partition> getPartitionsGroupReference()
    {
        return partitionsGroupReference;
    }

    public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector()
    {
        return streamProcessorServiceFactoryInjector;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public Injector<DeploymentManagerRequestHandler> getRequestHandlerServiceInjector()
    {
        return requestHandlerServiceInjector;
    }
}
