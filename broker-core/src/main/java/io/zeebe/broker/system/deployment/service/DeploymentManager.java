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
import io.zeebe.broker.logstreams.processor.StreamProcessorIds;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.system.SystemServiceNames;
import io.zeebe.broker.system.deployment.data.DeploymentPositionByWorkflowKey;
import io.zeebe.broker.system.deployment.data.LastWorkflowKey;
import io.zeebe.broker.system.deployment.data.LatestVersionByProcessIdAndTopicName;
import io.zeebe.broker.system.deployment.data.TopicNames;
import io.zeebe.broker.system.deployment.data.WorkflowKeyByProcessIdAndVersion;
import io.zeebe.broker.system.deployment.processor.DeploymentCreateEventProcessor;
import io.zeebe.broker.system.deployment.processor.DeploymentCreatedEventProcess;
import io.zeebe.broker.system.deployment.processor.DeploymentRejectedEventProcessor;
import io.zeebe.broker.system.deployment.processor.DeploymentTopicCreatingEventProcessor;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandlerManager;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ServerTransport;

public class DeploymentManager implements Service<DeploymentManager>
{
    private final ServiceGroupReference<Partition> partitionsGroupReference = ServiceGroupReference.<Partition>create()
        .onAdd((name, partition) -> installServices(partition, name))
        .build();

    private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector = new Injector<>();
    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<DeploymentManagerRequestHandler> requestHandlerServiceInjector = new Injector<>();
    private final Injector<ControlMessageHandlerManager> controlMessageHandlerManagerServiceInjector = new Injector<>();

    private ServerTransport clientApiTransport;
    private StreamProcessorServiceFactory streamProcessorServiceFactory;

    private DeploymentManagerRequestHandler requestHandlerService;

    private ServiceStartContext startContext;

    private RequestWorkflowControlMessageHandler controlMessageHandler;

    @Override
    public void start(ServiceStartContext startContext)
    {
        this.startContext = startContext;
        this.clientApiTransport = clientApiTransportInjector.getValue();
        this.streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();
        this.requestHandlerService = requestHandlerServiceInjector.getValue();

        controlMessageHandler = new RequestWorkflowControlMessageHandler(clientApiTransport.getOutput());

        controlMessageHandlerManagerServiceInjector.getValue().registerHandler(controlMessageHandler);
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
            .onCommand(ValueType.DEPLOYMENT, Intent.CREATE, new DeploymentCreateEventProcessor(latestVersionByProcessIdAndTopicName, lastWorkflowKey, topicNames))
            .onEvent(ValueType.DEPLOYMENT, Intent.CREATED, new DeploymentCreatedEventProcess(deploymentPositionByWorkflowKey, workflowKeyByProcessIdAndVersion))
            .onRejection(ValueType.DEPLOYMENT, Intent.CREATE, new DeploymentRejectedEventProcessor())
            .onEvent(ValueType.TOPIC, Intent.CREATING, new DeploymentTopicCreatingEventProcessor(topicNames))
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

                    final WorkflowRepositoryService workflowRepositoryService = new WorkflowRepositoryService(ctx.getActorControl(),
                        workflowKeyByProcessIdAndVersion,
                        latestVersionByProcessIdAndTopicName,
                        cache);

                    startContext.createService(SystemServiceNames.REPOSITORY_SERVICE, workflowRepositoryService)
                        .dependency(partitionServiceName)
                        .install();

                    final FetchWorkflowRequestHandler requestHandler = new FetchWorkflowRequestHandler(workflowRepositoryService);
                    requestHandlerService.setFetchWorkflowRequestHandler(requestHandler);

                    controlMessageHandler.setWorkflowRepositoryService(workflowRepositoryService);
                }

                @Override
                public void onClose()
                {
                    requestHandlerService.setFetchWorkflowRequestHandler(null);
                    controlMessageHandler.setWorkflowRepositoryService(null);

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

    public Injector<ControlMessageHandlerManager> getControlMessageHandlerManagerServiceInjector()
    {
        return controlMessageHandlerManagerServiceInjector;
    }
}
