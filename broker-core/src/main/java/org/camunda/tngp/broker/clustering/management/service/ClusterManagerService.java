package org.camunda.tngp.broker.clustering.management.service;

import org.camunda.tngp.broker.clustering.management.ClusterManager;
import org.camunda.tngp.broker.clustering.management.ClusterManagerContext;
import org.camunda.tngp.broker.clustering.management.config.ClusterManagementConfig;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.servicecontainer.*;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;

public class ClusterManagerService implements Service<ClusterManager>
{
    private final Injector<ClusterManagerContext> clusterManagementContextInjector = new Injector<>();
    private Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    private ClusterManager clusterManager;
    private ClusterManagementConfig config;
    private ServiceContainer serviceContainer;

    private ActorReference actorRef;

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
            final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();

            clusterManager = new ClusterManager(context, serviceContainer, config);
            clusterManager.open();

            actorRef = actorScheduler.schedule(clusterManager);
        });

    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        actorRef.close();
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

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

}
