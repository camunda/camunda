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
package io.zeebe.broker.clustering.base.topology;

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class TopologyManagerService implements Service<TopologyManager> {
  private TopologyManagerImpl topologyManager;

  private final ServiceGroupReference<Partition> leaderInstallReference =
      ServiceGroupReference.<Partition>create()
          .onAdd(
              (name, partition) ->
                  topologyManager.updateRole(partition.getState(), partition.getPartitionId()))
          .build();

  private final ServiceGroupReference<Partition> followerInstallReference =
      ServiceGroupReference.<Partition>create()
          .onAdd(
              (name, partition) ->
                  topologyManager.updateRole(partition.getState(), partition.getPartitionId()))
          .build();

  private final NodeInfo localMember;
  private final ClusterCfg clusterCfg;
  private final Injector<Atomix> atomixInjector = new Injector<>();

  public TopologyManagerService(NodeInfo localMember, ClusterCfg clusterCfg) {
    this.localMember = localMember;
    this.clusterCfg = clusterCfg;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final Atomix atomix = atomixInjector.getValue();

    topologyManager = new TopologyManagerImpl(atomix, localMember, clusterCfg);

    startContext.async(startContext.getScheduler().submitActor(topologyManager));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(topologyManager.close());
  }

  @Override
  public TopologyManager get() {
    return topologyManager;
  }

  public ServiceGroupReference<Partition> getLeaderInstallReference() {
    return leaderInstallReference;
  }

  public ServiceGroupReference<Partition> getFollowerInstallReference() {
    return followerInstallReference;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
