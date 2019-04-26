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

import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_HOST;
import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_PORT_OFFSET;

import io.zeebe.util.Environment;

public class NetworkCfg implements ConfigurationEntry {

  public static final String DEFAULT_HOST = "0.0.0.0";

  private String host = DEFAULT_HOST;
  private String defaultSendBufferSize = "16M";
  private int portOffset = 0;

  private SocketBindingClientApiCfg client = new SocketBindingClientApiCfg();
  private SocketBindingAtomixCfg atomix = new SocketBindingAtomixCfg();

  @Override
  public void init(
      final BrokerCfg brokerCfg, final String brokerBase, final Environment environment) {
    applyEnvironment(environment);
    client.applyDefaults(this);
    atomix.applyDefaults(this);
  }

  private void applyEnvironment(final Environment environment) {
    environment.get(ENV_HOST).ifPresent(v -> host = v);
    environment.getInt(ENV_PORT_OFFSET).ifPresent(v -> portOffset = v);
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public String getDefaultSendBufferSize() {
    return defaultSendBufferSize;
  }

  public void setDefaultSendBufferSize(final String defaultSendBufferSize) {
    this.defaultSendBufferSize = defaultSendBufferSize;
  }

  public int getPortOffset() {
    return portOffset;
  }

  public void setPortOffset(final int portOffset) {
    this.portOffset = portOffset;
  }

  public SocketBindingClientApiCfg getClient() {
    return client;
  }

  public void setClient(final SocketBindingClientApiCfg clientApi) {
    this.client = clientApi;
  }

  public SocketBindingAtomixCfg getAtomix() {
    return atomix;
  }

  public void setAtomix(SocketBindingAtomixCfg atomix) {
    this.atomix = atomix;
  }

  @Override
  public String toString() {
    return "NetworkCfg{"
        + "host='"
        + host
        + '\''
        + ", defaultSendBufferSize='"
        + defaultSendBufferSize
        + '\''
        + ", portOffset="
        + portOffset
        + ", client="
        + client
        + '}';
  }
}
