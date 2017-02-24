package org.camunda.tngp.broker.clustering.gossip.service;

import org.camunda.tngp.broker.clustering.gossip.Gossip;
import org.camunda.tngp.broker.clustering.gossip.GossipContext;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class GossipService implements Service<Gossip>
{
    private final Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();
    private final Injector<GossipContext> gossipContextInjector = new Injector<>();

    private Gossip gossip;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final AgentRunnerServices agentRunnerServices = agentRunnerInjector.getValue();
        final GossipContext context = gossipContextInjector.getValue();

        startContext.run(() ->
        {
            this.gossip = new Gossip(context);
            gossip.open();
            agentRunnerServices.gossipAgentRunnerService().run(gossip);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        final AgentRunnerServices agentRunnerServices = agentRunnerInjector.getValue();
        agentRunnerServices.gossipAgentRunnerService().remove(gossip);
    }

    @Override
    public Gossip get()
    {
        return gossip;
    }

    public Injector<GossipContext> getGossipContextInjector()
    {
        return gossipContextInjector;
    }

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

}
