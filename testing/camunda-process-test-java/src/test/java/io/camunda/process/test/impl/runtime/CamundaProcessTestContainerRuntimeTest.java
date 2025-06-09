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
package io.camunda.process.test.impl.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ConnectorsContainer;
import io.camunda.process.test.impl.containers.ContainerFactory;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(
    strictness = Strictness.LENIENT) // prevent error when global instance doesn't require mocking
public class CamundaProcessTestContainerRuntimeTest {

  private static final Map<String, String> ENV_VARS;
  private static final String ADDITIONAL_ENV_VAR_KEY = "env-3";
  private static final String ADDITIONAL_ENV_VAR_VALUE = "test-3";
  private static final Map<String, String> EXPECTED_ENV_VARS;

  static {
    ENV_VARS = new HashMap<>();
    ENV_VARS.put("env-1", "test-1");
    ENV_VARS.put("env-2", "test-2");

    EXPECTED_ENV_VARS = new HashMap<>(ENV_VARS);
    EXPECTED_ENV_VARS.put(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE);
  }

  @Mock private ContainerFactory containerFactory;

  @Mock(answer = Answers.RETURNS_SELF)
  private CamundaContainer camundaContainer;

  @Mock(answer = Answers.RETURNS_SELF)
  private ConnectorsContainer connectorsContainer;

  @BeforeEach
  void configureMocks() {
    when(containerFactory.createCamundaContainer(any(), any())).thenReturn(camundaContainer);
    when(containerFactory.createConnectorsContainer(any(), any())).thenReturn(connectorsContainer);
  }

  private static Stream<Arguments> provideCptConfigurations() {
    return Stream.of(
        Arguments.of(
            Named.of(
                "withCamundaEnv",
                CamundaProcessTestContainerRuntime.newBuilder().withCamundaEnv(ENV_VARS))),
        Arguments.of(
            Named.of(
                "withCamundaEnv",
                CamundaProcessTestContainerRuntime.newBuilder().withCamundaEnv("key", "value"))),
        Arguments.of(
            Named.of(
                "withCamundaDockerImageName",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withCamundaDockerImageName("image"))),
        Arguments.of(
            Named.of(
                "withCamundaDockerImageVersion",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withCamundaDockerImageVersion("version"))),
        Arguments.of(
            Named.of(
                "withElasticsearchDockerImageName",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withElasticsearchDockerImageName("image"))),
        Arguments.of(
            Named.of(
                "withElasticsearchDockerImageVersion",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withElasticsearchDockerImageVersion("version"))),
        Arguments.of(
            Named.of(
                "withConnectorsDockerImageName",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withConnectorsDockerImageName("image"))),
        Arguments.of(
            Named.of(
                "withConnectorsDockerImageVersion",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withConnectorsDockerImageVersion("version"))),
        Arguments.of(
            Named.of(
                "withElasticsearchEnv",
                CamundaProcessTestContainerRuntime.newBuilder().withElasticsearchEnv(ENV_VARS))),
        Arguments.of(
            Named.of(
                "withElasticsearchEnv",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withElasticsearchEnv("key", "value"))),
        Arguments.of(
            Named.of(
                "withConnectorsEnv",
                CamundaProcessTestContainerRuntime.newBuilder().withConnectorsEnv(ENV_VARS))),
        Arguments.of(
            Named.of(
                "withCamundaExposedPort",
                CamundaProcessTestContainerRuntime.newBuilder().withCamundaExposedPort(8080))),
        Arguments.of(
            Named.of(
                "withElasticsearchExposedPort",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withElasticsearchExposedPort(8080))),
        Arguments.of(
            Named.of(
                "withConnectorsExposedPort",
                CamundaProcessTestContainerRuntime.newBuilder().withConnectorsExposedPort(8080))),
        Arguments.of(
            Named.of(
                "withCamundaLogger",
                CamundaProcessTestContainerRuntime.newBuilder().withCamundaLogger("logger"))),
        Arguments.of(
            Named.of(
                "withElasticsearchLogger",
                CamundaProcessTestContainerRuntime.newBuilder().withElasticsearchLogger("logger"))),
        Arguments.of(
            Named.of(
                "withConnectorsLogger",
                CamundaProcessTestContainerRuntime.newBuilder().withConnectorsLogger("logger"))),
        Arguments.of(
            Named.of(
                "withConnectorsEnabled",
                CamundaProcessTestContainerRuntime.newBuilder().withConnectorsEnabled(true))),
        Arguments.of(
            Named.of(
                "withRemoteCamundaMonitoringApiAddress",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withRemoteCamundaMonitoringApiAddress(URI.create("https://www.example.com")))),
        Arguments.of(
            Named.of(
                "withRemoteConnectorsRestApiAddress",
                CamundaProcessTestContainerRuntime.newBuilder()
                    .withRemoteConnectorsRestApiAddress(URI.create("https://www.example.com")))));
  }

  @Test
  void shouldCreateContainers() {
    // given/when
    final CamundaProcessTestContainerRuntime runtime =
        (CamundaProcessTestContainerRuntime)
            CamundaProcessTestContainerRuntime.newBuilder()
                .withContainerFactory(containerFactory)
                .withLocalRuntime()
                .build();

    // then
    assertThat(runtime).isNotNull();
    assertThat(runtime.getCamundaContainer()).isEqualTo(camundaContainer);
    assertThat(runtime.getConnectorsContainer()).isEqualTo(connectorsContainer);

    verify(camundaContainer, never()).start();
    verify(connectorsContainer, never()).start();
  }

  @Test
  void shouldStartAndStopContainers() throws Exception {
    // given
    final CamundaProcessTestContainerRuntime runtime =
        (CamundaProcessTestContainerRuntime)
            CamundaProcessTestContainerRuntime.newBuilder()
                .withContainerFactory(containerFactory)
                .withLocalRuntime()
                .build();

    // when
    runtime.start();

    // then
    verify(camundaContainer).start();
    verify(connectorsContainer, never()).start();

    // and when
    runtime.close();

    // then
    verify(camundaContainer).stop();
    verify(connectorsContainer, never()).stop();
  }

  @Test
  void shouldCreateWithDefaults() {
    // given/when
    CamundaProcessTestContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withLocalRuntime()
        .build();

    // then
    verify(containerFactory)
        .createCamundaContainer(
            CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME,
            CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);
    verify(containerFactory)
        .createConnectorsContainer(
            CamundaProcessTestRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_NAME,
            CamundaProcessTestRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_VERSION);
  }

  @Test
  void shouldUseGlobalRuntimeWithDefaults() {
    // given/when
    final CamundaProcessTestRuntime runtime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withContainerFactory(containerFactory)
            .build();

    // then
    assertThat(runtime).isNotNull().isInstanceOf(CamundaProcessTestGlobalContainerRuntime.class);
  }

  @ParameterizedTest
  @MethodSource("provideCptConfigurations")
  void shouldUseLocalRuntimeIfConfigurationChanged(CamundaProcessTestRuntimeBuilder builder) {

    // given/when
    final CamundaProcessTestRuntime runtime =
        builder.withContainerFactory(containerFactory).build();

    // then
    assertThat(runtime).isNotNull().isNotInstanceOf(CamundaProcessTestGlobalContainerRuntime.class);
  }

  @Test
  void shouldConfigureCamundaContainer() {
    // given
    final String dockerImageName = "custom-camunda";
    final String dockerImageVersion = "8.6.0-custom";

    // when
    CamundaProcessTestContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withCamundaDockerImageName(dockerImageName)
        .withCamundaDockerImageVersion(dockerImageVersion)
        .withCamundaEnv(ENV_VARS)
        .withCamundaEnv(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE)
        .withCamundaExposedPort(100)
        .withCamundaExposedPort(200)
        .withCamundaLogger("custom-logger")
        .build();

    // then
    verify(containerFactory).createCamundaContainer(dockerImageName, dockerImageVersion);
    verify(camundaContainer).withEnv(EXPECTED_ENV_VARS);
    verify(camundaContainer).addExposedPort(100);
    verify(camundaContainer).addExposedPort(200);
    verify(camundaContainer).withLogConsumer(any());
  }

  // The ES container has been removed in favor of a H2 database solution. However, in the future ES
  // is going to be configurable as part of {@see https://github.com/camunda/camunda/issues/29854}
  @Disabled
  void shouldConfigureElasticsearchContainer() {
    // given
    final String dockerImageName = "custom-elasticsearch";
    final String dockerImageVersion = "8.13.0-custom";

    // when
    CamundaProcessTestContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withElasticsearchDockerImageName(dockerImageName)
        .withElasticsearchDockerImageVersion(dockerImageVersion)
        .withElasticsearchEnv(ENV_VARS)
        .withElasticsearchEnv(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE)
        .withElasticsearchExposedPort(100)
        .withElasticsearchExposedPort(200)
        .withElasticsearchLogger("custom-logger")
        .build();

    // then
    verify(containerFactory).createElasticsearchContainer(dockerImageName, dockerImageVersion);
  }

  @Test
  void shouldEnableConnectors() throws Exception {
    // given
    final CamundaProcessTestContainerRuntime runtime =
        (CamundaProcessTestContainerRuntime)
            CamundaProcessTestContainerRuntime.newBuilder()
                .withContainerFactory(containerFactory)
                .withConnectorsEnabled(true)
                .build();

    // when
    runtime.start();

    // then
    verify(connectorsContainer).start();

    // and when
    runtime.close();

    // then
    verify(connectorsContainer).stop();
  }

  @Test
  void shouldConfigureConnectorsContainer() {
    // given
    final String dockerImageName = "custom-connectors";
    final String dockerImageVersion = "8.6.0-custom";

    final Map<String, String> connectorSecrets = new HashMap<>();
    connectorSecrets.put("secret-1", "1");
    connectorSecrets.put("secret-2", "2");

    final String additionalConnectorSecretKey = "secret-3";
    final String additionalConnectorSecretValue = "3";

    final Map<String, String> expectedConnectorSecrets = new HashMap<>(connectorSecrets);
    expectedConnectorSecrets.put(additionalConnectorSecretKey, additionalConnectorSecretValue);

    // when
    CamundaProcessTestContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withConnectorsDockerImageName(dockerImageName)
        .withConnectorsDockerImageVersion(dockerImageVersion)
        .withConnectorsEnv(ENV_VARS)
        .withConnectorsEnv(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE)
        .withConnectorsExposedPort(100)
        .withConnectorsExposedPort(200)
        .withConnectorsSecrets(connectorSecrets)
        .withConnectorsSecret(additionalConnectorSecretKey, additionalConnectorSecretValue)
        .withConnectorsLogger("custom-logger")
        .build();

    // then
    verify(containerFactory).createConnectorsContainer(dockerImageName, dockerImageVersion);
    verify(connectorsContainer).withEnv(EXPECTED_ENV_VARS);
    verify(connectorsContainer).addExposedPort(100);
    verify(connectorsContainer).addExposedPort(200);
    verify(connectorsContainer).withEnv(expectedConnectorSecrets);
    verify(connectorsContainer).withLogConsumer(any());
  }
}
