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

import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.gossip.Gossip;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Join / leave cluster on broker start / stop */
public class GossipJoinService implements Service<Object> {
  private final Injector<Gossip> gossipInjector = new Injector<>();
  private final ClusterCfg clusterCfg;
  private Gossip gossip;

  public GossipJoinService(ClusterCfg clusterCfg) {
    this.clusterCfg = clusterCfg;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    gossip = gossipInjector.getValue();

    final List<SocketAddress> initalContactPoints =
        Arrays.stream(clusterCfg.getInitialContactPoints())
            .map(SocketAddress::from)
            .collect(Collectors.toList());

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
  public Object get() {
    return null;
  }

  public Injector<Gossip> getGossipInjector() {
    return gossipInjector;
  }
}
