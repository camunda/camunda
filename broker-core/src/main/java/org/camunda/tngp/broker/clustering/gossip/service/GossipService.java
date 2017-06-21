package org.camunda.tngp.broker.clustering.gossip.service;

import java.io.File;

import org.camunda.tngp.broker.clustering.gossip.Gossip;
import org.camunda.tngp.broker.clustering.gossip.GossipContext;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.LangUtil;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;

public class GossipService implements Service<Gossip>
{
    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private final Injector<GossipContext> gossipContextInjector = new Injector<>();

    private Gossip gossip;
    private GossipContext gossipContext;
    private ActorReference actorRef;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        this.gossipContext = gossipContextInjector.getValue();
        startContext.run(() ->
        {
            //create a gossip folder
            final GossipConfiguration configuration = gossipContext.getConfig();
            createFile(configuration.fileName());

            this.gossip = new Gossip(gossipContext);
            gossip.open();
            actorRef = actorScheduler.schedule(gossip);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        actorRef.close();
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

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
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
