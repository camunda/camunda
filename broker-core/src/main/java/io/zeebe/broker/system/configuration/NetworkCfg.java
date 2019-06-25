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

import io.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg.InternalApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg.MonitoringApiCfg;
import io.zeebe.util.Environment;

public class NetworkCfg implements ConfigurationEntry {

  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_COMMAND_API_PORT = 26501;
  public static final int DEFAULT_INTERNAL_API_PORT = 26502;
  public static final int DEFAULT_MONITORING_API_PORT = 9600;

  private String host = DEFAULT_HOST;
  private int portOffset = 0;

  private CommandApiCfg commandApi = new CommandApiCfg();
  private InternalApiCfg internalApi = new InternalApiCfg();
  private MonitoringApiCfg monitoringApi = new MonitoringApiCfg();

  @Override
  public void init(
      final BrokerCfg brokerCfg, final String brokerBase, final Environment environment) {
    applyEnvironment(environment);
    commandApi.applyDefaults(this);
    internalApi.applyDefaults(this);
    monitoringApi.applyDefaults(this);
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

  public int getPortOffset() {
    return portOffset;
  }

  public void setPortOffset(final int portOffset) {
    this.portOffset = portOffset;
  }

  public SocketBindingCfg getCommandApi() {
    return commandApi;
  }

  public void setCommandApi(final CommandApiCfg commandApi) {
    this.commandApi = commandApi;
  }

  public SocketBindingCfg getMonitoringApi() {
    return monitoringApi;
  }

  public void setMonitoringApi(MonitoringApiCfg monitoringApi) {
    this.monitoringApi = monitoringApi;
  }

  public SocketBindingCfg getInternalApi() {
    return internalApi;
  }

  public void setInternalApi(InternalApiCfg internalApi) {
    this.internalApi = internalApi;
  }

  @Override
  public String toString() {
    return "NetworkCfg{"
        + "host='"
        + host
        + '\''
        + ", portOffset="
        + portOffset
        + ", commandApi="
        + commandApi
        + ", internalApi="
        + internalApi
        + ", monitoringApi="
        + monitoringApi
        + '}';
  }
}
