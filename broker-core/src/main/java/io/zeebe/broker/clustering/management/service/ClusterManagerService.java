/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.management.service;

import io.zeebe.broker.clustering.management.ClusterManager;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.broker.clustering.management.config.ClusterManagementConfig;
import io.zeebe.raft.Raft;
import io.zeebe.servicecontainer.*;
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
            .onAdd((name, raft) -> clusterManager.addRaft(name, raft))
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
