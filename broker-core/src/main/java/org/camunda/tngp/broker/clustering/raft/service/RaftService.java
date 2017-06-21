package org.camunda.tngp.broker.clustering.raft.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.MetaStore;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;

public class RaftService implements Service<Raft>
{
    private final LogStream logStream;
    private final List<Member> members;

    private Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private Injector<RaftContext> raftContextInjector = new Injector<>();

    private Raft raft;
    private MetaStore meta;
    private boolean bootstrap;
    private ActorReference actorRef;

    public RaftService(final LogStream logStream, final MetaStore meta, final List<Member> members, boolean bootstrap)
    {
        this.logStream = logStream;
        this.meta = meta;
        this.members = members;
        this.bootstrap = bootstrap;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
            final RaftContext raftContext = raftContextInjector.getValue();

            raft = new Raft(raftContext, logStream, meta);

            if (bootstrap)
            {
                raft.bootstrap();
            }
            else
            {
                raft.join(members);
            }

            actorRef = actorScheduler.schedule(raft);
        });
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

        raft.closeAsync().thenAccept((v) ->
        {
            actorRef.close();

            raft.stream().closeAsync().whenComplete((v2, t) ->
            {
                closeFuture.complete(null);
            });
        });

        ctx.async(closeFuture);

    }

    @Override
    public Raft get()
    {
        return raft;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<RaftContext> getRaftContextInjector()
    {
        return raftContextInjector;
    }
}
