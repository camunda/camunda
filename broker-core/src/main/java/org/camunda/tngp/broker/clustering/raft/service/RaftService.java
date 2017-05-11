package org.camunda.tngp.broker.clustering.raft.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.MetaStore;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class RaftService implements Service<Raft>
{
    private final LogStream logStream;
    private final List<Member> members;

    private Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();
    private Injector<RaftContext> raftContextInjector = new Injector<>();

    private Raft raft;
    private MetaStore meta;
    private boolean bootstrap;

    private CompletableFuture<Void> closeLogFuture;

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
            final AgentRunnerServices agentRunnerServices = agentRunnerInjector.getValue();
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

            agentRunnerServices.raftAgentRunnerService().run(raft);
        });
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        final CompletableFuture<Void> closeFuture = new CompletableFuture<Void>();

        raft.closeAsync().thenAccept((v) ->
        {
            final AgentRunnerServices agentRunnerService = agentRunnerInjector.getValue();
            agentRunnerService.raftAgentRunnerService().remove(raft);
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

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

    public Injector<RaftContext> getRaftContextInjector()
    {
        return raftContextInjector;
    }
}
