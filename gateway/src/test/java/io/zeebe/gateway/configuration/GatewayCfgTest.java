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
package io.zeebe.gateway.configuration;

import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CLUSTER_HOST;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CLUSTER_MEMBER_ID;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CLUSTER_NAME;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CLUSTER_PORT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CONTACT_POINT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_HOST;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MANAGEMENT_THREADS;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_PORT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_REQUEST_TIMEOUT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_TRANSPORT_BUFFER;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.util.Environment;
import io.zeebe.util.TomlConfigurationReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class GatewayCfgTest {

  private static final String DEFAULT_CFG_FILENAME = "/configuration/gateway.default.toml";
  private static final GatewayCfg DEFAULT_CFG = new GatewayCfg();
  private static final String CUSTOM_CFG_FILENAME = "/configuration/gateway.custom.toml";
  private static final GatewayCfg CUSTOM_CFG = new GatewayCfg();

  static {
    DEFAULT_CFG.init();
    CUSTOM_CFG.init();
    CUSTOM_CFG.getNetwork().setHost("192.168.0.1").setPort(123);
    CUSTOM_CFG
        .getCluster()
        .setContactPoint("foobar:1234")
        .setTransportBuffer("4K")
        .setRequestTimeout("123h")
        .setClusterName("testCluster")
        .setMemberId("testMember")
        .setHost("1.2.3.4")
        .setPort(12321);
    CUSTOM_CFG.getThreads().setManagementThreads(100);
  }

  private final Map<String, String> environment = new HashMap<>();

  @Test
  public void shouldHaveDefaultValues() {
    // when
    final GatewayCfg gatewayCfg = readDefaultConfig();

    // then
    assertThat(gatewayCfg).isEqualTo(DEFAULT_CFG);
  }

  @Test
  public void shouldLoadCustomConfig() {
    // when
    final GatewayCfg gatewayCfg = readCustomConfig();

    // then
    assertThat(gatewayCfg).isEqualTo(CUSTOM_CFG);
  }

  @Test
  public void shouldUseEnvironmentVariables() {
    // given
    setEnv(ENV_GATEWAY_HOST, "zeebe");
    setEnv(ENV_GATEWAY_PORT, "5432");
    setEnv(ENV_GATEWAY_CONTACT_POINT, "broker:432");
    setEnv(ENV_GATEWAY_TRANSPORT_BUFFER, "12G");
    setEnv(ENV_GATEWAY_MANAGEMENT_THREADS, "32");
    setEnv(ENV_GATEWAY_REQUEST_TIMEOUT, "43m");
    setEnv(ENV_GATEWAY_CLUSTER_NAME, "envCluster");
    setEnv(ENV_GATEWAY_CLUSTER_MEMBER_ID, "envMember");
    setEnv(ENV_GATEWAY_CLUSTER_HOST, "envHost");
    setEnv(ENV_GATEWAY_CLUSTER_PORT, "12345");

    final GatewayCfg expected = new GatewayCfg();
    expected.getNetwork().setHost("zeebe").setPort(5432);
    expected
        .getCluster()
        .setContactPoint("broker:432")
        .setTransportBuffer("12G")
        .setRequestTimeout("43m")
        .setClusterName("envCluster")
        .setMemberId("envMember")
        .setHost("envHost")
        .setPort(12345);
    expected.getThreads().setManagementThreads(32);

    // when
    final GatewayCfg gatewayCfg = readCustomConfig();

    // then
    assertThat(gatewayCfg).isEqualTo(expected);
  }

  private void setEnv(String key, String value) {
    environment.put(key, value);
  }

  private GatewayCfg readDefaultConfig() {
    return readConfig(DEFAULT_CFG_FILENAME);
  }

  private GatewayCfg readCustomConfig() {
    return readConfig(CUSTOM_CFG_FILENAME);
  }

  private GatewayCfg readConfig(String filename) {
    try (InputStream inputStream = GatewayCfgTest.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        final GatewayCfg gatewayCfg = TomlConfigurationReader.read(inputStream, GatewayCfg.class);
        gatewayCfg.init(new Environment(environment));
        return gatewayCfg;
      } else {
        throw new AssertionError("Unable to find configuration file: " + filename);
      }
    } catch (IOException e) {
      throw new AssertionError("Failed to read configuration from file: " + filename, e);
    }
  }
}
