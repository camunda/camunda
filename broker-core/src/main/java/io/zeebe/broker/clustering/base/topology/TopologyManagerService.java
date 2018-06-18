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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LOCAL_NODE;

import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.gossip.Gossip;
import io.zeebe.raft.Raft;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.SocketAddress;

public class TopologyManagerService implements Service<TopologyManager> {
  private TopologyManagerImpl topologyManager;

  private final Injector<Gossip> gossipInjector = new Injector<>();

  private final ServiceGroupReference<Raft> raftReference =
      ServiceGroupReference.<Raft>create()
          .onAdd((name, raft) -> topologyManager.onRaftStarted(raft))
          .onRemove((name, raft) -> topologyManager.onRaftRemoved(raft))
          .build();

  private final NodeInfo localMember;

  public TopologyManagerService(NetworkCfg cfg) {
    final SocketAddress managementApi = cfg.getManagement().toSocketAddress();
    final SocketAddress clientApi = cfg.getClient().toSocketAddress();
    final SocketAddress replicationApi = cfg.getReplication().toSocketAddress();

    localMember = new NodeInfo(clientApi, managementApi, replicationApi);
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final Gossip gossip = gossipInjector.getValue();

    topologyManager = new TopologyManagerImpl(gossip, localMember);

    startContext.createService(LOCAL_NODE, new LocalNodeService(localMember)).install();

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

  public ServiceGroupReference<Raft> getRaftReference() {
    return raftReference;
  }

  public Injector<Gossip> getGossipInjector() {
    return gossipInjector;
  }
}
