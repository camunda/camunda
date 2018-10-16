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
package io.zeebe.broker.transport;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.system.configuration.SocketBindingClientApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingGatewayCfg;
import io.zeebe.gateway.Gateway;
import java.io.IOException;

public class GatewayComponent implements Component {

  @Override
  public void init(final SystemContext context) {
    final NetworkCfg network = context.getBrokerConfiguration().getNetwork();
    final SocketBindingGatewayCfg gatewayCfg = network.getGateway();
    if (gatewayCfg.isEnabled()) {
      try {
        final SocketBindingClientApiCfg clientApiCfg = network.getClient();
        final Gateway gateway = new Gateway(gatewayCfg.getHost(), gatewayCfg.getPort());
        // TODO(menski): help this is horrible
        gateway.setBrokerContactPoint(clientApiCfg.toSocketAddress().toString());
        gateway.start();
        context.addResourceReleasingDelegate(gateway::stop);
      } catch (final IOException e) {
        throw new RuntimeException("Gateway was not able to start", e);
      }
    }
  }
}
