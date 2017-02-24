package org.camunda.tngp.broker.clustering.management.service;

import org.camunda.tngp.broker.clustering.management.ClusterManager;
import org.camunda.tngp.broker.clustering.management.ClusterManagerContext;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class ClusterManagerService implements Service<ClusterManager>
{
    private final Injector<ClusterManagerContext> clusterManagementContextInjector = new Injector<>();
    private Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();

    private ClusterManager clusterManager;

    private final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> clusterManager.addStream(stream))
            .build();

    private final ServiceGroupReference<Raft> raftGroupReference = ServiceGroupReference.<Raft>create()
            .onAdd((name, raft) -> clusterManager.addRaft(raft))
            .onRemove((name, raft) -> clusterManager.removeRaft(raft))
            .build();

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClusterManagerContext context = clusterManagementContextInjector.getValue();
        final AgentRunnerServices agentRunner = agentRunnerInjector.getValue();

        clusterManager = new ClusterManager(context, startContext);
        agentRunner.clusterAgentService().run(clusterManager);

    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        final AgentRunnerServices agentRunner = agentRunnerInjector.getValue();
        agentRunner.clusterAgentService().remove(clusterManager);
    }

    @Override
    public ClusterManager get()
    {
        return clusterManager;
    }

    public Injector<ClusterManagerContext> getClusterManagementContextInjector()
    {
        return clusterManagementContextInjector;
    }


    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

    public ServiceGroupReference<Raft> getRaftGroupReference()
    {
        return raftGroupReference;
    }

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

}
