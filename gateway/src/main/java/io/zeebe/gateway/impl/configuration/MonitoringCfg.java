/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MONITORING_ENABLED;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MONITORING_PORT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MONITORING_ENABLED;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MONITORING_HOST;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MONITORING_PORT;

import io.zeebe.transport.SocketAddress;
import io.zeebe.util.Environment;
import java.util.Objects;

public class MonitoringCfg {

  private boolean enabled = DEFAULT_MONITORING_ENABLED;

  private String host;
  private int port = DEFAULT_MONITORING_PORT;

  public void init(Environment environment, String defaultHost) {
    environment.getBool(ENV_GATEWAY_MONITORING_ENABLED).ifPresent(this::setEnabled);
    environment.get(ENV_GATEWAY_MONITORING_HOST).ifPresent(this::setHost);
    environment.getInt(ENV_GATEWAY_MONITORING_PORT).ifPresent(this::setPort);

    if (host == null) {
      host = defaultHost;
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public MonitoringCfg setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public String getHost() {
    return host;
  }

  public MonitoringCfg setHost(String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public MonitoringCfg setPort(int port) {
    this.port = port;
    return this;
  }

  public SocketAddress toSocketAddress() {
    return new SocketAddress(host, port);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MonitoringCfg that = (MonitoringCfg) o;
    return enabled == that.enabled && port == that.port && Objects.equals(host, that.host);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, host, port);
  }

  @Override
  public String toString() {
    return "MonitoringCfg{"
        + "enabled="
        + enabled
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + '}';
  }
}
