package org.camunda.tngp.broker.clustering.gossip.service;

import java.io.File;

import org.camunda.tngp.broker.clustering.gossip.Gossip;
import org.camunda.tngp.broker.clustering.gossip.GossipContext;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.LangUtil;

public class GossipService implements Service<Gossip>
{
    private final Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();
    private final Injector<GossipContext> gossipContextInjector = new Injector<>();

    private Gossip gossip;
    private GossipContext gossipContext;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final AgentRunnerServices agentRunnerServices = agentRunnerInjector.getValue();
        this.gossipContext = gossipContextInjector.getValue();
        startContext.run(() ->
        {
            //create a gossip folder
            final GossipConfiguration configuration = gossipContext.getConfig();
            createFile(configuration.fileName());

            this.gossip = new Gossip(gossipContext);
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

    private void createFile(String file)
    {
        final File f = new File(file);
        if (!f.exists())
        {
            try
            {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }
            catch (Exception e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }
    }
}
