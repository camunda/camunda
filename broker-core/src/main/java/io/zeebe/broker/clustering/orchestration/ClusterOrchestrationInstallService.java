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
package io.zeebe.broker.clustering.orchestration;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.TOPOLOGY_MANAGER_SERVICE;
import static io.zeebe.broker.clustering.orchestration.ClusterOrchestrationLayerServiceNames.*;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY;
import static io.zeebe.broker.transport.TransportServiceNames.*;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.orchestration.id.IdGenerator;
import io.zeebe.broker.clustering.orchestration.state.KnownTopics;
import io.zeebe.broker.clustering.orchestration.topic.ReplicationFactorService;
import io.zeebe.broker.clustering.orchestration.topic.RequestPartitionsMessageHandler;
import io.zeebe.broker.clustering.orchestration.topic.TopicCreationService;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandlerManager;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerTransport;
import org.slf4j.Logger;

public class ClusterOrchestrationInstallService implements Service<Void>
{

    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final Injector<ControlMessageHandlerManager> controlMessageHandlerManagerInjector = new Injector<>();
    private final Injector<ServerTransport> transportInjector = new Injector<>();

    private final ServiceGroupReference<Partition> systemLeaderGroupReference = ServiceGroupReference.<Partition>create()
        .onAdd(this::installClusterOrchestrationServices)
        .onRemove(this::removeClusterOrchestrationServices)
        .build();

    private ServiceStartContext startContext;

    private RequestPartitionsMessageHandler requestPartitionsMessageHandler;
    private final ServiceContainer serviceContainer;

    public ClusterOrchestrationInstallService(ServiceContainer serviceContainer)
    {
        this.serviceContainer = serviceContainer;
    }

    @Override
    public void start(final ServiceStartContext startContext)
    {
        this.startContext = startContext;

        final ServerOutput serverOutput = transportInjector.getValue().getOutput();
        requestPartitionsMessageHandler = new RequestPartitionsMessageHandler(serverOutput);

        final ControlMessageHandlerManager controlMessageHandlerManager = controlMessageHandlerManagerInjector.getValue();
        controlMessageHandlerManager.registerHandler(requestPartitionsMessageHandler);
    }

    private void installClusterOrchestrationServices(final ServiceName<Partition> partitionServiceName, final Partition partition)
    {
        final CompositeServiceBuilder compositeInstall = startContext.createComposite(CLUSTER_ORCHESTRATION_COMPOSITE_SERVICE_NAME);

        final KnownTopics knownTopics = new KnownTopics(serviceContainer);
        compositeInstall.createService(KNOWN_TOPICS_SERVICE_NAME, knownTopics)
                    .dependency(partitionServiceName, knownTopics.getPartitionInjector())
                    .dependency(serverTransport(CLIENT_API_SERVER_NAME), knownTopics.getServerTransportInjector())
                    .dependency(STREAM_PROCESSOR_SERVICE_FACTORY, knownTopics.getStreamProcessorServiceFactoryInjector())
                    .install();

        final IdGenerator idGenerator = new IdGenerator();
        compositeInstall.createService(ID_GENERATOR_SERVICE_NAME, idGenerator)
                        .dependency(serverTransport(CLIENT_API_SERVER_NAME), idGenerator.getClientApiTransportInjector())
                        .dependency(STREAM_PROCESSOR_SERVICE_FACTORY, idGenerator.getStreamProcessorServiceFactoryInjector())
                        .dependency(partitionServiceName, idGenerator.getPartitionInjector())
                        .install();

        final NodeSelector nodeSelector = new NodeSelector();
        compositeInstall.createService(NODE_SELECTOR_SERVICE_NAME, nodeSelector)
            .dependency(TOPOLOGY_MANAGER_SERVICE, nodeSelector.getTopologyManagerInjector())
            .install();

        final TopicCreationService topicCreationService = new TopicCreationService();
        compositeInstall.createService(TOPIC_CREATION_SERVICE_NAME, topicCreationService)
                        .dependency(KNOWN_TOPICS_SERVICE_NAME, topicCreationService.getStateInjector())
                        .dependency(TOPOLOGY_MANAGER_SERVICE, topicCreationService.getTopologyManagerInjector())
                        .dependency(partitionServiceName, topicCreationService.getLeaderSystemPartitionInjector())
                        .dependency(ID_GENERATOR_SERVICE_NAME, topicCreationService.getIdGeneratorInjector())
                        .dependency(NODE_SELECTOR_SERVICE_NAME, topicCreationService.getNodeOrchestratingServiceInjector())
                        .dependency(clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME), topicCreationService.getManagementClientApiInjector())
                        .install();

        final ReplicationFactorService replicationFactorService = new ReplicationFactorService();
        compositeInstall.createService(REPLICATION_FACTOR_SERVICE_NAME, replicationFactorService)
                        .dependency(KNOWN_TOPICS_SERVICE_NAME, replicationFactorService.getStateInjector())
                        .dependency(TOPOLOGY_MANAGER_SERVICE, replicationFactorService.getTopologyManagerInjector())
                        .dependency(NODE_SELECTOR_SERVICE_NAME, replicationFactorService.getNodeOrchestratingServiceInjector())
                        .dependency(clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME), replicationFactorService.getManagementClientApiInjector())
                        .install();

        compositeInstall.createService(REQUEST_PARTITIONS_MESSAGE_HANDLER_SERVICE_NAME, requestPartitionsMessageHandler)
                        .dependency(KNOWN_TOPICS_SERVICE_NAME, requestPartitionsMessageHandler.getClusterTopicStateInjector())
                        .install();


        compositeInstall.install();

        LOG.debug("Installing cluster orchestration services");
    }

    private void removeClusterOrchestrationServices(final ServiceName<Partition> partitionServiceName, final Partition partition)
    {
        startContext.removeService(CLUSTER_ORCHESTRATION_COMPOSITE_SERVICE_NAME);
        LOG.debug("Removing cluster orchestration services");
    }

    @Override
    public Void get()
    {
        return null;
    }

    public Injector<ControlMessageHandlerManager> getControlMessageHandlerManagerInjector()
    {
        return controlMessageHandlerManagerInjector;
    }

    public Injector<ServerTransport> getTransportInjector()
    {
        return transportInjector;
    }

    public ServiceGroupReference<Partition> getSystemLeaderGroupReference()
    {
        return systemLeaderGroupReference;
    }

}
