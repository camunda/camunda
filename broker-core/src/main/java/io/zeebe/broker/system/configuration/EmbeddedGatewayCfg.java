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
package io.zeebe.broker.system.configuration;

import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_EMBED_GATEWAY;

import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.util.Environment;

public class EmbeddedGatewayCfg extends GatewayCfg implements ConfigurationEntry {

  private boolean enable = true;

  @Override
  public void init(BrokerCfg globalConfig, String brokerBase, Environment environment) {
    environment.getBool(ENV_EMBED_GATEWAY).ifPresent(this::setEnable);

    // configure gateway based on broker network settings
    final NetworkCfg networkCfg = globalConfig.getNetwork();

    // network host precedence from higher to lower:
    // 1. ENV_GATEWAY_HOST
    // 2. specified in gateway toml section
    // 3. ENV_HOST
    // 4. specified in broker network toml section
    init(environment, networkCfg.getHost());

    // ensure embedded gateway can access local broker
    getCluster().setContactPoint(networkCfg.getAtomix().toSocketAddress().toString());

    // configure embedded gateway based on broker config
    getNetwork().setPort(getNetwork().getPort() + (networkCfg.getPortOffset() * 10));
  }

  public boolean isEnable() {
    return enable;
  }

  public EmbeddedGatewayCfg setEnable(boolean enable) {
    this.enable = enable;
    return this;
  }
}
