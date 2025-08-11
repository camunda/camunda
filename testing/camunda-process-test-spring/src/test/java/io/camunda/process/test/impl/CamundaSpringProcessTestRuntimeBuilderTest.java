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

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration.RemoteConfiguration;
import io.camunda.process.test.impl.runtime.CamundaProcessTestContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRemoteRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.process.test.impl.runtime.CamundaSpringProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.runtime.ContainerRuntimePorts;
import io.camunda.spring.client.properties.CamundaClientAuthProperties;
import io.camunda.spring.client.properties.CamundaClientCloudProperties;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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

    assertThat(runtimeBuilder.getCamundaDockerImageName())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME);
    assertThat(runtimeBuilder.getCamundaDockerImageVersion())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);
    assertThat(runtimeBuilder.getCamundaEnvVars()).isEmpty();
    assertThat(runtimeBuilder.getCamundaExposedPorts()).isEmpty();

    assertThat(runtimeBuilder.isConnectorsEnabled()).isFalse();
  }

  @Test
  void shouldConfigureManagedCamundaRuntime() {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

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

  @Test
  void shouldConfigureManagedConnectorsRuntime() {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

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

    final CamundaClientBuilderFactory remoteCamundaClientBuilderFactory =
        runtimeBuilder.getRemoteCamundaClientBuilderFactory();
    final CamundaClientBuilder remoteClientBuilder = remoteCamundaClientBuilderFactory.get();
    final CamundaClientConfiguration configuration =
        getCamundaClientConfiguration(remoteClientBuilder);

    assertThat(configuration.getRestAddress())
        .hasHost("0.0.0.0")
        .hasPort(ContainerRuntimePorts.CAMUNDA_REST_API);

    assertThat(configuration.getGrpcAddress())
        .hasHost("0.0.0.0")
        .hasPort(ContainerRuntimePorts.CAMUNDA_GATEWAY_API);

    assertThat(configuration.isPlaintextConnectionEnabled()).isTrue();
  }

  @Test
  void shouldConfigureRemoteRuntimeLocal() {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

    final URI remoteCamundaRestApiAddress = URI.create("http://camunda.com:1000");
    final URI remoteCamundaGrpcApiAddress = URI.create("http://camunda.com:2000");
    final URI remoteCamundaMonitoringApiAddress = URI.create("http://camunda.com:3000");
    final URI remoteConnectorsRestApiAddress = URI.create("http://camunda.com:4000");

    runtimeConfiguration.setRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE);

    final RemoteConfiguration remoteConfiguration = runtimeConfiguration.getRemote();
    remoteConfiguration.setCamundaMonitoringApiAddress(remoteCamundaMonitoringApiAddress);
    remoteConfiguration.setConnectorsRestApiAddress(remoteConnectorsRestApiAddress);

    final CamundaClientProperties remoteClientProperties = remoteConfiguration.getClient();
    remoteClientProperties.setRestAddress(remoteCamundaRestApiAddress);
    remoteClientProperties.setGrpcAddress(remoteCamundaGrpcApiAddress);

    // when
    CamundaSpringProcessTestRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);

    // then
    assertThat(runtimeBuilder.getRemoteCamundaMonitoringApiAddress())
        .isEqualTo(remoteCamundaMonitoringApiAddress);
    assertThat(runtimeBuilder.getRemoteConnectorsRestApiAddress())
        .isEqualTo(remoteConnectorsRestApiAddress);

    final CamundaClientBuilderFactory remoteCamundaClientBuilderFactory =
        runtimeBuilder.getRemoteCamundaClientBuilderFactory();
    final CamundaClientBuilder remoteClientBuilder = remoteCamundaClientBuilderFactory.get();

    final CamundaClientConfiguration configuration =
        getCamundaClientConfiguration(remoteClientBuilder);

    assertThat(configuration.getRestAddress()).isEqualTo(remoteCamundaRestApiAddress);
    assertThat(configuration.getGrpcAddress()).isEqualTo(remoteCamundaGrpcApiAddress);
    assertThat(configuration.isPlaintextConnectionEnabled()).isTrue();
  }

  @Test
  void shouldConfigureRemoteRuntimeSaaS() {
    // given
    final CamundaProcessTestRuntimeBuilder runtimeBuilder = new CamundaProcessTestRuntimeBuilder();
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        new CamundaProcessTestRuntimeConfiguration();

    runtimeConfiguration.setRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE);

    final RemoteConfiguration remoteConfiguration = runtimeConfiguration.getRemote();
    final CamundaClientProperties remoteClientProperties = remoteConfiguration.getClient();
    remoteClientProperties.setMode(ClientMode.saas);

    final CamundaClientCloudProperties cloudProperties = remoteClientProperties.getCloud();
    cloudProperties.setClusterId("my-cluster");
    cloudProperties.setRegion("my-region");

    final CamundaClientAuthProperties authProperties = remoteClientProperties.getAuth();
    authProperties.setClientId("my-client-id");
    authProperties.setClientSecret("my-client-secret");

    // when
    CamundaSpringProcessTestRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);

    // then
    final CamundaClientBuilderFactory remoteCamundaClientBuilderFactory =
        runtimeBuilder.getRemoteCamundaClientBuilderFactory();
    final CamundaClientBuilder remoteClientBuilder = remoteCamundaClientBuilderFactory.get();

    final CamundaClientConfiguration configuration =
        getCamundaClientConfiguration(remoteClientBuilder);

    assertThat(configuration.getRestAddress())
        .isEqualTo(URI.create("https://my-region.zeebe.camunda.io:443/my-cluster"));
    assertThat(configuration.getGrpcAddress())
        .isEqualTo(URI.create("https://my-cluster.my-region.zeebe.camunda.io:443"));

    assertThat(configuration.getCredentialsProvider()).isInstanceOf(OAuthCredentialsProvider.class);
  }

  private static CamundaClientConfiguration getCamundaClientConfiguration(
      final CamundaClientBuilder camundaClientBuilder) {
    try (final CamundaClient camundaClient = camundaClientBuilder.build()) {
      return camundaClient.getConfiguration();
    }
  }
}
