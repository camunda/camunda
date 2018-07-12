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
package io.zeebe.broker.system;

import static io.zeebe.broker.system.configuration.NetworkCfg.ENV_PORT_OFFSET;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.Environment;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.system.configuration.SocketBindingClientApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingManagementCfg;
import io.zeebe.broker.system.configuration.SocketBindingReplicationCfg;
import io.zeebe.broker.system.configuration.TomlConfigurationReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ConfigurationTest {

  public Map<String, String> environment = new HashMap<>();

  public static final int CLIENT_PORT = SocketBindingClientApiCfg.DEFAULT_PORT;
  public static final int MANAGEMENT_PORT = SocketBindingManagementCfg.DEFAULT_PORT;
  public static final int REPLICATION_PORT = SocketBindingReplicationCfg.DEFAULT_PORT;

  @Test
  public void shouldUseDefaultPorts() {
    assertPorts("default", CLIENT_PORT, MANAGEMENT_PORT, REPLICATION_PORT);
  }

  @Test
  public void shouldUseSpecifiedPorts() {
    assertPorts("specific-ports", 1, 2, 3);
  }

  @Test
  public void shouldUsePortOffset() {
    final int offset = 50;
    assertPorts(
        "port-offset", CLIENT_PORT + offset, MANAGEMENT_PORT + offset, REPLICATION_PORT + offset);
  }

  @Test
  public void shouldUsePortOffsetWithSpecifiedPorts() {
    final int offset = 30;
    assertPorts("specific-ports-offset", 1 + offset, 2 + offset, 3 + offset);
  }

  @Test
  public void shouldUsePortOffsetFromEnvironment() {
    environment.put(ENV_PORT_OFFSET, "5");
    final int offset = 50;
    assertPorts(
        "default", CLIENT_PORT + offset, MANAGEMENT_PORT + offset, REPLICATION_PORT + offset);
  }

  @Test
  public void shouldUsePortOffsetFromEnvironmentWithSpecifiedPorts() {
    environment.put(ENV_PORT_OFFSET, "3");
    final int offset = 30;
    assertPorts("specific-ports", 1 + offset, 2 + offset, 3 + offset);
  }

  @Test
  public void shouldIgnoreInvalidPortOffsetFromEnvironment() {
    environment.put(ENV_PORT_OFFSET, "a");
    assertPorts("default", CLIENT_PORT, MANAGEMENT_PORT, REPLICATION_PORT);
  }

  @Test
  public void shouldOverridePortOffsetFromEnvironment() {
    environment.put(ENV_PORT_OFFSET, "7");
    final int offset = 70;
    assertPorts(
        "port-offset", CLIENT_PORT + offset, MANAGEMENT_PORT + offset, REPLICATION_PORT + offset);
  }

  private BrokerCfg readConfig(String name) {
    final InputStream resourceAsStream =
        ConfigurationTest.class.getResourceAsStream("/system/" + name + ".toml");
    assertThat(resourceAsStream).isNotNull();

    final BrokerCfg config = TomlConfigurationReader.read(resourceAsStream);
    config.init("test", new Environment(environment));
    return config;
  }

  private void assertPorts(String configFileName, int client, int management, int replication) {
    final NetworkCfg network = readConfig(configFileName).getNetwork();
    assertThat(network.getClient().getPort()).isEqualTo(client);
    assertThat(network.getManagement().getPort()).isEqualTo(management);
    assertThat(network.getReplication().getPort()).isEqualTo(replication);
  }
}
