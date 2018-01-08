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
package io.zeebe.broker.clustering.management;

import io.zeebe.broker.clustering.management.memberList.MemberListService;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.broker.system.deployment.handler.WorkflowRequestMessageHandler;
import io.zeebe.gossip.Gossip;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.actor.ActorScheduler;

public class ClusterManagerContext
{
    private ActorScheduler actorScheduler;
    private LogStreamsManager logStreamsManager;
    private WorkflowRequestMessageHandler workflowRequestMessageHandler;
    private ClientTransport clientTransport;
    private BufferingServerTransport serverTransport;
    private Gossip gossip;
    private MemberListService memberListService;

    public ActorScheduler getActorScheduler()
    {
        return actorScheduler;
    }

    public void setActorScheduler(ActorScheduler actorScheduler)
    {
        this.actorScheduler = actorScheduler;
    }

    public Gossip getGossip()
    {
        return gossip;
    }

    public void setGossip(Gossip gossip)
    {
        this.gossip = gossip;
    }

    public MemberListService getMemberListService()
    {
        return memberListService;
    }

    public void setMemberListService(MemberListService memberListService)
    {
        this.memberListService = memberListService;
    }

    public BufferingServerTransport getServerTransport()
    {
        return serverTransport;
    }

    public void setServerTransport(BufferingServerTransport serverTransport)
    {
        this.serverTransport = serverTransport;
    }

    public ClientTransport getClientTransport()
    {
        return clientTransport;
    }

    public void setClientTransport(ClientTransport clientTransport)
    {
        this.clientTransport = clientTransport;
    }

    public LogStreamsManager getLogStreamsManager()
    {
        return logStreamsManager;
    }

    public void setLogStreamsManager(LogStreamsManager logStreamsManager)
    {
        this.logStreamsManager = logStreamsManager;
    }

    public WorkflowRequestMessageHandler getWorkflowRequestMessageHandler()
    {
        return workflowRequestMessageHandler;
    }

    public void setWorkflowRequestMessageHandler(WorkflowRequestMessageHandler workflowRequestMessageHandler)
    {
        this.workflowRequestMessageHandler = workflowRequestMessageHandler;
    }

}
