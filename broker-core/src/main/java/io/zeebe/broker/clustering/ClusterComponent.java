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
package io.zeebe.broker.clustering;

import static io.zeebe.broker.clustering.ClusterServiceNames.*;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.LOG_STREAMS_MANAGER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.WORKFLOW_REQUEST_MESSAGE_HANDLER_SERVICE;
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_SERVER_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.REPLICATION_API_CLIENT_NAME;

import io.zeebe.broker.clustering.gossip.service.GossipService;
import io.zeebe.broker.clustering.management.memberList.MemberListService;
import io.zeebe.broker.clustering.management.service.ClusterManagerContextService;
import io.zeebe.broker.clustering.management.service.ClusterManagerService;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.servicecontainer.ServiceContainer;

public class ClusterComponent implements Component
{

    @Override
    public void init(final SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();
        final TransportComponentCfg config = configurationManager.readEntry("network", TransportComponentCfg.class);

        initMemberList(serviceContainer);
        initGossip(serviceContainer, config);
        initClusterManager(serviceContainer, config);
    }

    protected void initMemberList(final ServiceContainer serviceContainer)
    {
        final MemberListService memberListService = new MemberListService();
        serviceContainer.createService(MEMBER_LIST_SERVICE, memberListService)
                        .install();

    }

    protected void initGossip(final ServiceContainer serviceContainer, final TransportComponentCfg config)
    {
        final GossipService gossipService = new GossipService(config);
        serviceContainer.createService(GOSSIP_SERVICE, gossipService)
            .dependency(ACTOR_SCHEDULER_SERVICE, gossipService.getActorSchedulerInjector())
            .dependency(TransportServiceNames.clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME), gossipService.getClientTransportInjector())
            .dependency(TransportServiceNames.bufferingServerTransport(TransportServiceNames.MANAGEMENT_API_SERVER_NAME), gossipService.getBufferingServerTransportInjector())
            .install();
    }

    protected void initClusterManager(final ServiceContainer serviceContainer, final TransportComponentCfg config)
    {
        final ClusterManagerContextService clusterManagementContextService = new ClusterManagerContextService();
        serviceContainer.createService(CLUSTER_MANAGER_CONTEXT_SERVICE, clusterManagementContextService)
            .dependency(TransportServiceNames.bufferingServerTransport(MANAGEMENT_API_SERVER_NAME), clusterManagementContextService.getManagementApiTransportInjector())
            .dependency(TransportServiceNames.clientTransport(MANAGEMENT_API_CLIENT_NAME), clusterManagementContextService.getManagementClientInjector())
            .dependency(TransportServiceNames.clientTransport(REPLICATION_API_CLIENT_NAME), clusterManagementContextService.getReplicationClientInjector())
            .dependency(MEMBER_LIST_SERVICE, clusterManagementContextService.getMemberListServiceInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, clusterManagementContextService.getActorSchedulerInjector())
            .dependency(LOG_STREAMS_MANAGER_SERVICE, clusterManagementContextService.getLogStreamsManagerInjector())
            .dependency(WORKFLOW_REQUEST_MESSAGE_HANDLER_SERVICE, clusterManagementContextService.getWorkflowRequestMessageHandlerInjector())
            .dependency(GOSSIP_SERVICE, clusterManagementContextService.getGossipInjector())
            .install();

        final ClusterManagerService clusterManagerService = new ClusterManagerService(serviceContainer, config);
        serviceContainer.createService(CLUSTER_MANAGER_SERVICE, clusterManagerService)
            .dependency(CLUSTER_MANAGER_CONTEXT_SERVICE, clusterManagerService.getClusterManagementContextInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, clusterManagerService.getActorSchedulerInjector())
            .groupReference(RAFT_SERVICE_GROUP, clusterManagerService.getRaftGroupReference())
            .install();
    }

}
