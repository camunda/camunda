package org.camunda.tngp.broker.clustering.raft.service;

import java.util.List;

import org.camunda.tngp.broker.clustering.raft.Member;
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

    public RaftService(final LogStream logStream, final List<Member> members)
    {
        this.logStream = logStream;
        this.members = members;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final AgentRunnerServices agentRunnerServices = agentRunnerInjector.getValue();
            final RaftContext raftContext = raftContextInjector.getValue();

            raft = new Raft(raftContext, logStream);

            if (members.isEmpty())
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
    public void stop(ServiceStopContext arg0)
    {
        // TODO: close raft so that running state machine releases used resources

        final AgentRunnerServices agentRunnerService = agentRunnerInjector.getValue();
        agentRunnerService.raftAgentRunnerService().remove(raft);
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
