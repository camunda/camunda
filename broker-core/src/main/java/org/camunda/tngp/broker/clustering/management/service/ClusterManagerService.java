package org.camunda.tngp.broker.clustering.management.service;

import org.camunda.tngp.broker.clustering.management.ClusterManager;
import org.camunda.tngp.broker.clustering.management.ClusterManagerContext;
import org.camunda.tngp.broker.clustering.management.config.ClusterManagementConfig;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class ClusterManagerService implements Service<ClusterManager>
{
    private final Injector<ClusterManagerContext> clusterManagementContextInjector = new Injector<>();
    private Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();

    private ClusterManager clusterManager;
    private ClusterManagementConfig config;
    private ServiceContainer serviceContainer;

    public ClusterManagerService(final ServiceContainer serviceContainer, final ClusterManagementConfig config)
    {
        this.serviceContainer = serviceContainer;
        this.config = config;
    }

    private final ServiceGroupReference<Raft> raftGroupReference = ServiceGroupReference.<Raft>create()
            .onAdd((name, raft) -> clusterManager.addRaft(raft))
            .onRemove((name, raft) -> clusterManager.removeRaft(raft))
            .build();

    @Override
    public void start(ServiceStartContext startContext)
    {
        startContext.run(() ->
        {
            final ClusterManagerContext context = clusterManagementContextInjector.getValue();
            final AgentRunnerServices agentRunner = agentRunnerInjector.getValue();

            clusterManager = new ClusterManager(context, serviceContainer, config);
            clusterManager.open();

            agentRunner.clusterAgentService().run(clusterManager);
        });

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


    public ServiceGroupReference<Raft> getRaftGroupReference()
    {
        return raftGroupReference;
    }

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

}
