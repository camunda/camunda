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
package io.camunda.process.test.impl;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.RemoteConfiguration;
import io.camunda.process.test.impl.runtime.CamundaProcessTestContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRemoteRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.process.test.impl.runtime.CamundaSpringProcessTestRuntimeBuilder;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class CamundaSpringProcessTestRuntimeBuilderTest {

  @Test
  void shouldBuildManagedRuntimeByDefault() {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

    // when
    final CamundaProcessTestRuntime camundaRuntime =
        CamundaSpringProcessTestRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);

    // then
    assertThat(camundaRuntime).isNotNull().isInstanceOf(CamundaProcessTestContainerRuntime.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = CamundaProcessTestRuntimeMode.class,
      names = {"MANAGED"})
  void shouldBuildDefaultRuntime(final CamundaProcessTestRuntimeMode runtimeMode) {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

    runtimeConfiguration.setRuntimeMode(runtimeMode);

    // when
    CamundaSpringProcessTestRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);

    // then
    assertThat(runtimeBuilder.getCamundaDockerImageName())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME);
    assertThat(runtimeBuilder.getCamundaDockerImageVersion())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);
    assertThat(runtimeBuilder.getCamundaEnvVars()).isEmpty();
    assertThat(runtimeBuilder.getCamundaExposedPorts()).isEmpty();

    assertThat(runtimeBuilder.isConnectorsEnabled()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = CamundaProcessTestRuntimeMode.class,
      names = {"MANAGED"})
  void shouldConfigureCamundaRuntime(final CamundaProcessTestRuntimeMode runtimeMode) {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

    runtimeConfiguration.setRuntimeMode(runtimeMode);

    final Map<String, String> camundaEnvVars =
        Map.ofEntries(entry("env-1", "test-1"), entry("env-2", "test-2"));

    runtimeConfiguration.setCamundaDockerImageVersion("8.6.0-custom");
    runtimeConfiguration.setCamundaDockerImageName("custom-camunda");
    runtimeConfiguration.setCamundaEnvVars(camundaEnvVars);
    final List<Integer> camundaExposedPorts = List.of(100, 200);
    runtimeConfiguration.setCamundaExposedPorts(camundaExposedPorts);
    runtimeConfiguration.setCamundaLoggerName("io.camunda.custom.logger.name");
    runtimeConfiguration.setConnectorsLoggerName("io.camunda.custom.logger.name");

    // when
    CamundaSpringProcessTestRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);

    // then
    assertThat(runtimeBuilder.getCamundaDockerImageName()).isEqualTo("custom-camunda");
    assertThat(runtimeBuilder.getCamundaDockerImageVersion()).isEqualTo("8.6.0-custom");
    assertThat(runtimeBuilder.getCamundaEnvVars()).isEqualTo(camundaEnvVars);
    assertThat(runtimeBuilder.getCamundaExposedPorts()).isEqualTo(camundaExposedPorts);
    assertThat(runtimeBuilder.getCamundaLoggerName()).isEqualTo("io.camunda.custom.logger.name");
    assertThat(runtimeBuilder.getConnectorsLoggerName()).isEqualTo("io.camunda.custom.logger.name");
  }

  @ParameterizedTest
  @EnumSource(
      value = CamundaProcessTestRuntimeMode.class,
      names = {"MANAGED"})
  void shouldConfigureConnectorsRuntime(final CamundaProcessTestRuntimeMode runtimeMode) {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

    runtimeConfiguration.setRuntimeMode(runtimeMode);

    final Map<String, String> connectorsEnvVars =
        Map.ofEntries(entry("env-1", "test-1"), entry("env-2", "test-2"));

    final Map<String, String> connectorsSecrets =
        Map.ofEntries(entry("secret-1", "1"), entry("secret-2", "2"));

    runtimeConfiguration.setConnectorsEnabled(true);
    runtimeConfiguration.setConnectorsDockerImageName("custom-connectors");
    runtimeConfiguration.setConnectorsDockerImageVersion("8.6.0-custom");
    runtimeConfiguration.setConnectorsEnvVars(connectorsEnvVars);
    runtimeConfiguration.setConnectorsSecrets(connectorsSecrets);
    runtimeConfiguration.setConnectorsExposedPorts(List.of(9090));

    // when
    CamundaSpringProcessTestRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);

    // then
    assertThat(runtimeBuilder.isConnectorsEnabled()).isTrue();
    assertThat(runtimeBuilder.getConnectorsDockerImageName()).isEqualTo("custom-connectors");
    assertThat(runtimeBuilder.getConnectorsDockerImageVersion()).isEqualTo("8.6.0-custom");
    assertThat(runtimeBuilder.getConnectorsEnvVars()).isEqualTo(connectorsEnvVars);
    assertThat(runtimeBuilder.getConnectorsSecrets()).isEqualTo(connectorsSecrets);
    assertThat(runtimeBuilder.getConnectorsExposedPorts()).isEqualTo(List.of(9090));
  }

  @Test
  void shouldBuildDefaultRemoteRuntime() {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

    runtimeConfiguration.setRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE);

    // when
    final CamundaProcessTestRuntime camundaRuntime =
        CamundaSpringProcessTestRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);

    // then
    assertThat(camundaRuntime).isNotNull().isInstanceOf(CamundaProcessTestRemoteRuntime.class);

    assertThat(runtimeBuilder.getRemoteCamundaMonitoringApiAddress())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS);
    assertThat(runtimeBuilder.getRemoteConnectorsRestApiAddress())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS);
    assertThat(runtimeBuilder.getRemoteRuntimeConnectionTimeout())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.REMOTE_RUNTIME_CONNECTION_TIMEOUT);
  }

  @Test
  void shouldConfigureRemoteRuntimeAddresses() {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

    final URI remoteCamundaMonitoringApiAddress = URI.create("http://camunda.com:3000");
    final URI remoteConnectorsRestApiAddress = URI.create("http://camunda.com:4000");
    final Duration remoteRuntimeConnectionTimeout = Duration.ofSeconds(30);

    runtimeConfiguration.setRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE);

    final RemoteConfiguration remoteConfiguration = runtimeConfiguration.getRemote();
    remoteConfiguration.setCamundaMonitoringApiAddress(remoteCamundaMonitoringApiAddress);
    remoteConfiguration.setConnectorsRestApiAddress(remoteConnectorsRestApiAddress);
    remoteConfiguration.setRuntimeConnectionTimeout(remoteRuntimeConnectionTimeout);

    // when
    CamundaSpringProcessTestRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);

    // then
    assertThat(runtimeBuilder.getRemoteCamundaMonitoringApiAddress())
        .isEqualTo(remoteCamundaMonitoringApiAddress);
    assertThat(runtimeBuilder.getRemoteConnectorsRestApiAddress())
        .isEqualTo(remoteConnectorsRestApiAddress);
    assertThat(runtimeBuilder.getRemoteRuntimeConnectionTimeout())
        .isEqualTo(remoteRuntimeConnectionTimeout);
  }
}
