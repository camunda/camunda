package io.zeebe.broker.clustering.gossip.service;

import java.io.File;

import io.zeebe.broker.clustering.gossip.Gossip;
import io.zeebe.broker.clustering.gossip.GossipContext;
import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.LangUtil;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

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
