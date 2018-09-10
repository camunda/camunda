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
package io.zeebe.broker.clustering.base.gossip;

import static io.zeebe.broker.clustering.base.gossip.GossipCustomEventEncoding.writeNodeInfo;
import static io.zeebe.broker.clustering.base.topology.TopologyManagerImpl.CONTACT_POINTS_EVENT_TYPE;

import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.gossip.Gossip;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.SocketAddress;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

/** Join / leave cluster on broker start / stop */
public class GossipJoinService implements Service<Void> {
  private final Injector<Gossip> gossipInjector = new Injector<>();
  private final ClusterCfg clusterCfg;
  private final NodeInfo localMember;
  private Gossip gossip;

  public GossipJoinService(ClusterCfg clusterCfg, NodeInfo localMember) {
    this.clusterCfg = clusterCfg;
    this.localMember = localMember;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    gossip = gossipInjector.getValue();

    final List<SocketAddress> initalContactPoints =
        clusterCfg
            .getInitialContactPoints()
            .stream()
            .map(SocketAddress::from)
            .collect(Collectors.toList());

    publishLocalContactPoints();

    if (!initalContactPoints.isEmpty()) {
      // TODO: check if join is retrying internally on failure.
      startContext.async(gossip.join(initalContactPoints));
    }
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    // TODO: check if leave has timeout
    stopContext.async(gossip.leave());
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<Gossip> getGossipInjector() {
    return gossipInjector;
  }

  private void publishLocalContactPoints() {
    final MutableDirectBuffer eventBuffer = new ExpandableArrayBuffer();
    final int eventLength = writeNodeInfo(localMember, eventBuffer, 0);

    gossip.publishEvent(CONTACT_POINTS_EVENT_TYPE, eventBuffer, 0, eventLength);
  }
}
