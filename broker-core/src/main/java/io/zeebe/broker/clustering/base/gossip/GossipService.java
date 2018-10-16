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

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.gossip.Gossip;
import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;

/** Start / stop gossip on broker start / stop */
public class GossipService implements Service<Gossip> {
  private final Injector<ClientTransport> clientTransportInjector = new Injector<>();
  private final Injector<BufferingServerTransport> bufferingServerTransportInjector =
      new Injector<>();
  private final BrokerCfg configuration;

  private Gossip gossip;

  public GossipService(final BrokerCfg configuration) {
    this.configuration = configuration;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final BufferingServerTransport serverTransport = bufferingServerTransportInjector.getValue();
    final ClientTransport clientTransport = clientTransportInjector.getValue();

    final GossipConfiguration gossipConfiguration = configuration.getGossip();

    gossip =
        new Gossip(
            configuration.getCluster().getNodeId(),
            serverTransport,
            clientTransport,
            gossipConfiguration);

    startContext.async(startContext.getScheduler().submitActor(gossip));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(gossip.close());
  }

  @Override
  public Gossip get() {
    return gossip;
  }

  public Injector<ClientTransport> getClientTransportInjector() {
    return clientTransportInjector;
  }

  public Injector<BufferingServerTransport> getBufferingServerTransportInjector() {
    return bufferingServerTransportInjector;
  }
}
