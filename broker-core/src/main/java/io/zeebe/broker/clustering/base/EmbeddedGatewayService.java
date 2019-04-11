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
package io.zeebe.broker.clustering.base;

import io.atomix.cluster.AtomixCluster;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.gateway.Gateway;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import java.io.IOException;
import java.util.function.Function;

public class EmbeddedGatewayService implements Service<Gateway> {

  private final BrokerCfg configuration;
  private final Injector<AtomixCluster> atomixClusterInjector = new Injector<>();

  private Gateway gateway;

  public EmbeddedGatewayService(BrokerCfg configuration) {
    this.configuration = configuration;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final AtomixCluster atomix = atomixClusterInjector.getValue();
    final Function<GatewayCfg, BrokerClient> brokerClientFactory =
        cfg -> new BrokerClientImpl(cfg, atomix, startContext.getScheduler(), false);
    gateway = new Gateway(configuration.getGateway(), brokerClientFactory);
    startContext.run(this::startGateway);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    if (gateway != null) {
      stopContext.run(gateway::stop);
    }
  }

  private void startGateway() {
    try {
      gateway.start();
    } catch (final IOException e) {
      throw new RuntimeException("Gateway was not able to start", e);
    }
  }

  @Override
  public Gateway get() {
    return gateway;
  }

  public Injector<AtomixCluster> getAtomixClusterInjector() {
    return atomixClusterInjector;
  }
}
