package io.zeebe.broker.clustering.management.service;

import io.zeebe.broker.clustering.management.ClusterManager;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.broker.clustering.management.config.ClusterManagementConfig;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

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
