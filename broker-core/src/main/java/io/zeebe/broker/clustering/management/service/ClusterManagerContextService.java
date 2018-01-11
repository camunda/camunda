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
package io.zeebe.broker.clustering.management.service;

import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.broker.clustering.management.memberList.MemberListService;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.broker.system.deployment.handler.WorkflowRequestMessageHandler;
import io.zeebe.gossip.Gossip;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.actor.ActorScheduler;

public class ClusterManagerContextService implements Service<ClusterManagerContext>
{
    private final Injector<ClientTransport> managementClientInjector = new Injector<>();
    private final Injector<ClientTransport> replicationClientInjector = new Injector<>();
    private final Injector<BufferingServerTransport> managementApiTransportInjector = new Injector<>();

    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private final Injector<LogStreamsManager> logStreamsManagerInjector = new Injector<>();
    private final Injector<WorkflowRequestMessageHandler> workflowRequestMessageHandlerInjector = new Injector<>();
    private final Injector<MemberListService> memberListServiceInjector = new Injector<>();
    private final Injector<Gossip> gossipInjector = new Injector<>();

    private ClusterManagerContext context;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClientTransport clientTransport = managementClientInjector.getValue();
        final BufferingServerTransport serverTransport = managementApiTransportInjector.getValue();
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        final LogStreamsManager logStreamsManager = logStreamsManagerInjector.getValue();
        final WorkflowRequestMessageHandler workflowRequestMessageHandler = workflowRequestMessageHandlerInjector.getValue();

        context = new ClusterManagerContext();
        context.setGossip(gossipInjector.getValue());
        context.setActorScheduler(actorScheduler);
        context.setManagementClient(clientTransport);
        context.setReplicationClient(replicationClientInjector.getValue());
        context.setServerTransport(serverTransport);
        context.setMemberListService(memberListServiceInjector.getValue());
        context.setLogStreamsManager(logStreamsManager);
        context.setWorkflowRequestMessageHandler(workflowRequestMessageHandler);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public ClusterManagerContext get()
    {
        return context;
    }

    public Injector<MemberListService> getMemberListServiceInjector()
    {
        return memberListServiceInjector;
    }

    public Injector<Gossip> getGossipInjector()
    {
        return gossipInjector;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<LogStreamsManager> getLogStreamsManagerInjector()
    {
        return logStreamsManagerInjector;
    }

    public Injector<BufferingServerTransport> getManagementApiTransportInjector()
    {
        return managementApiTransportInjector;
    }

    public Injector<ClientTransport> getManagementClientInjector()
    {
        return managementClientInjector;
    }

    public Injector<ClientTransport> getReplicationClientInjector()
    {
        return replicationClientInjector;
    }

    public Injector<WorkflowRequestMessageHandler> getWorkflowRequestMessageHandlerInjector()
    {
        return workflowRequestMessageHandlerInjector;
    }

}
