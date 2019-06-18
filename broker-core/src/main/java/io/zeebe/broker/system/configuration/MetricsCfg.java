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

import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_METRICS_HTTP_SERVER;

import io.zeebe.util.Environment;

public class MetricsCfg implements ConfigurationEntry {

  public static final int DEFAULT_PORT = 9600;

  private boolean enableHttpServer = true;
  private String host;
  private int port = DEFAULT_PORT;

  @Override
  public void init(BrokerCfg brokerCfg, String brokerBase, Environment environment) {
    environment.getBool(ENV_METRICS_HTTP_SERVER).ifPresent(this::setEnableHttpServer);

    final NetworkCfg networkCfg = brokerCfg.getNetwork();
    if (host == null) {
      host = networkCfg.getHost();
    }
    port += networkCfg.getPortOffset() * 10;
  }

  public boolean isEnableHttpServer() {
    return enableHttpServer;
  }

  public MetricsCfg setEnableHttpServer(boolean enableHttpServer) {
    this.enableHttpServer = enableHttpServer;
    return this;
  }

  public String getHost() {
    return host;
  }

  public MetricsCfg setHost(String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public MetricsCfg setPort(int port) {
    this.port = port;
    return this;
  }

  @Override
  public String toString() {
    return "MetricsCfg{"
        + "enableHttpServer="
        + enableHttpServer
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + '}';
  }
}
