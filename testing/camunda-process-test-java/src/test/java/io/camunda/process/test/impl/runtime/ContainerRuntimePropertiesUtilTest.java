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

import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.runtime.properties.CamundaContainerRuntimeProperties;
import io.camunda.process.test.impl.runtime.properties.ConnectorsContainerRuntimeProperties;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ContainerRuntimePropertiesUtilTest {

  @Test
  void shouldReturnDefaults() {
    // given
    final Properties properties = new Properties();

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getCamundaVersion()).isEqualTo("SNAPSHOT");
    assertThat(propertiesUtil.getElasticsearchVersion()).isEqualTo("8.13.0");
    assertThat(propertiesUtil.getCamundaDockerImageName()).isEqualTo("camunda/camunda");
    assertThat(propertiesUtil.getCamundaDockerImageVersion()).isEqualTo("SNAPSHOT");
    assertThat(propertiesUtil.getConnectorsDockerImageName())
        .isEqualTo("camunda/connectors-bundle");
    assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo("SNAPSHOT");
  }

  @Test
  void shouldReturnDefaultsForDevelopment() {
    // given
    final Properties properties = new Properties();
    properties.put(
        CamundaContainerRuntimeProperties.PROPERTY_NAME_CAMUNDA_VERSION, "${project.version}");
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_ELASTICSEARCH_VERSION,
        "${version.elasticsearch}");
    properties.put(
        CamundaContainerRuntimeProperties.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME,
        "${io.camunda.process.test.camundaDockerImageName}");
    properties.put(
        CamundaContainerRuntimeProperties.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION,
        "${io.camunda.process.test.camundaDockerImageVersion}");
    properties.put(
        ConnectorsContainerRuntimeProperties.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME,
        "camunda/connectors-bundle");
    properties.put(
        ConnectorsContainerRuntimeProperties.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
        "${project.version}");

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getCamundaVersion()).isEqualTo("SNAPSHOT");
    assertThat(propertiesUtil.getElasticsearchVersion()).isEqualTo("8.13.0");
    assertThat(propertiesUtil.getCamundaDockerImageName()).isEqualTo("camunda/camunda");
    assertThat(propertiesUtil.getCamundaDockerImageVersion()).isEqualTo("SNAPSHOT");
    assertThat(propertiesUtil.getConnectorsDockerImageName())
        .isEqualTo("camunda/connectors-bundle");
    assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo("SNAPSHOT");
  }

  @ParameterizedTest
  @CsvSource({
    "8.6.0, 8.6.0",
    "8.6.1, 8.6.1",
    "8.6.0-SNAPSHOT, SNAPSHOT",
    "8.5.1-SNAPSHOT, 8.5.0",
    "8.5.2-SNAPSHOT, 8.5.1",
    "8.5.2-rc1, 8.5.2-rc1",
    "8.7.1-alpha4-rc1, 8.7.1-alpha4-rc1",
    "8.8.0-alpha4.1, 8.8.0-alpha4.1",
    "8.8.0-alpha4-optimize, 8.8.0-alpha4-optimize",
    "8.7.1-optimize, 8.7.1-optimize",
    "custom-version, custom-version"
  })
  void shouldReturnCamundaVersion(final String propertyVersion, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(
        CamundaContainerRuntimeProperties.PROPERTY_NAME_CAMUNDA_VERSION, propertyVersion);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getCamundaVersion()).isEqualTo(expectedVersion);
  }

  @ParameterizedTest
  @CsvSource({
    "8.13.1, 8.13.1",
    "8.14.0, 8.14.0",
    "8.14.0-rc1, 8.14.0-rc1",
    "custom-version, custom-version"
  })
  void shouldReturnElasticsearchVersion(
      final String propertyVersion, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_ELASTICSEARCH_VERSION, propertyVersion);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getElasticsearchVersion()).isEqualTo(expectedVersion);
  }

  @ParameterizedTest
  @CsvSource({
    "camunda/camunda, camunda/camunda",
    "camunda/current, camunda/current",
    "custom/camunda, custom/camunda",
    "my/custom, my/custom"
  })
  void shouldReturnCamundaDockerImageName(final String propertyName, final String expectedName) {
    // given
    final Properties properties = new Properties();
    properties.put(
        CamundaContainerRuntimeProperties.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME, propertyName);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getCamundaDockerImageName()).isEqualTo(expectedName);
  }

  @ParameterizedTest
  @CsvSource({
    "8.6.0, 8.6.0",
    "8.6.1, 8.6.1",
    "8.6.0-SNAPSHOT, SNAPSHOT",
    "8.5.1-SNAPSHOT, 8.5.0",
    "8.5.2-SNAPSHOT, 8.5.1",
    "8.5.2-rc1, 8.5.2-rc1",
    "8.7.1-alpha4-rc1, 8.7.1-alpha4-rc1",
    "8.8.0-alpha4.1, 8.8.0-alpha4.1",
    "8.8.0-alpha4-optimize, 8.8.0-alpha4-optimize",
    "8.7.1-optimize, 8.7.1-optimize",
    "custom-version, custom-version",
  })
  void shouldReturnCamundaDockerImageVersion(
      final String propertyVersion, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(
        CamundaContainerRuntimeProperties.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION,
        propertyVersion);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getCamundaDockerImageVersion()).isEqualTo(expectedVersion);
  }

  @ParameterizedTest
  @CsvSource({
    "camunda/connectors-bundle, camunda/connectors-bundle",
    "camunda/custom-connectors-bundle, camunda/custom-connectors-bundle",
    "my/connectors-bundle, my/connectors-bundle",
    "my/custom, my/custom"
  })
  void shouldReturnConnectorsDockerImageName(final String propertyName, final String expectedName) {
    // given
    final Properties properties = new Properties();
    properties.put(
        ConnectorsContainerRuntimeProperties.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME,
        propertyName);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getConnectorsDockerImageName()).isEqualTo(expectedName);
  }

  @ParameterizedTest
  @CsvSource({
    "8.6.0, 8.6.0",
    "8.6.1, 8.6.1",
    "8.6.0-SNAPSHOT, SNAPSHOT",
    "8.5.1-SNAPSHOT, 8.5.0",
    "8.5.2-SNAPSHOT, 8.5.1",
    "8.5.2-rc1, 8.5.2-rc1",
    "8.7.1-alpha4-rc1, 8.7.1-alpha4-rc1",
    "8.8.0-alpha4.1, 8.8.0-alpha4.1",
    "8.8.0-alpha4-optimize, 8.8.0-alpha4-optimize",
    "8.7.1-optimize, 8.7.1-optimize",
    "custom-version, custom-version"
  })
  void shouldReturnConnectorsDockerImageVersion(
      final String propertyVersion, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(
        ConnectorsContainerRuntimeProperties.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
        propertyVersion);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo(expectedVersion);
  }

  @Test
  void shouldParseCamundaEnvVars() {
    // given
    final Map<String, String> envVars = new HashMap<>();
    envVars.put("camundaEnvVars.keyA", "valueA");
    envVars.put("camundaEnvVars.keyB", "valueB");
    envVars.put("camundaEnvVars.keyC", "valueC");

    final Properties properties = new Properties();
    properties.putAll(envVars);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    final Map<String, String> expected = new HashMap<>();
    envVars.put("keyA", "valueA");
    envVars.put("keyB", "valueB");
    envVars.put("keyC", "valueC");

    assertThat(propertiesUtil.getCamundaEnvVars()).containsAllEntriesOf(expected);
  }

  @Test
  void shouldParseCamundaExposedPorts() {
    final Map<String, String> envVars = new HashMap<>();
    envVars.put("camundaExposedPorts[0]", "8080");
    envVars.put("camundaExposedPorts[1]", "8081");
    envVars.put("camundaExposedPorts[2]", "8088");

    final Properties properties = new Properties();
    properties.putAll(envVars);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    final List<Integer> expected = Arrays.asList(8080, 8081, 8088);

    assertThat(propertiesUtil.getCamundaExposedPorts()).containsAll(expected);
  }

  @Nested
  class UserOverridePropertiesFile {

    @Test
    public void shouldOverrideDefaults() {
      // when
      final ContainerRuntimePropertiesUtil propertiesUtil =
          ContainerRuntimePropertiesUtil.readProperties("/containerRuntimePropertiesUtil/");

      // then
      assertThat(propertiesUtil.getCamundaVersion()).isEqualTo("SNAPSHOT");
      assertThat(propertiesUtil.getElasticsearchVersion()).isEqualTo("1.1.0");
      assertThat(propertiesUtil.getCamundaDockerImageName()).isEqualTo("camunda/custom-camunda");
      assertThat(propertiesUtil.getCamundaDockerImageVersion()).isEqualTo("8.8.0-rc1");
      assertThat(propertiesUtil.getConnectorsDockerImageName())
          .isEqualTo("camunda/connectors-bundle");
      assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo("8.8.3");
    }

    @Test
    public void shouldHaveCustomConfigurationParams() {
      final ContainerRuntimePropertiesUtil propertiesUtil =
          ContainerRuntimePropertiesUtil.readProperties("/containerRuntimePropertiesUtil/");

      // then
      final Map<String, String> expectedCamundaEnvVars = new HashMap<>();
      expectedCamundaEnvVars.put("keyA", "valueA");
      expectedCamundaEnvVars.put("keyB", "valueB");
      expectedCamundaEnvVars.put("keyC", "valueC");

      final Map<String, String> expectedConnectorsEnvVars = new HashMap<>();
      expectedConnectorsEnvVars.put("keyD", "valueD");
      expectedConnectorsEnvVars.put("keyE", "valueE");
      expectedConnectorsEnvVars.put("keyF", "valueF");

      final Map<String, String> expectedConnectorsSecrets = new HashMap<>();
      expectedConnectorsSecrets.put("keyG", "secret1");
      expectedConnectorsSecrets.put("keyH", "secret2");
      expectedConnectorsSecrets.put("keyI", "secret3");

      assertThat(propertiesUtil.getCamundaExposedPorts())
          .containsExactlyInAnyOrder(8080, 8081, 8088);
      assertThat(propertiesUtil.getCamundaEnvVars()).containsAllEntriesOf(expectedCamundaEnvVars);

      assertThat(propertiesUtil.isConnectorsEnabled()).isTrue();
      assertThat(propertiesUtil.getConnectorsEnvVars())
          .containsAllEntriesOf(expectedConnectorsEnvVars);
      assertThat(propertiesUtil.getConnectorsSecrets())
          .containsAllEntriesOf(expectedConnectorsSecrets);

      assertThat(propertiesUtil.getRuntimeMode()).isEqualTo(CamundaProcessTestRuntimeMode.REMOTE);
      assertThat(propertiesUtil.getRemoteCamundaMonitoringApiAddress())
          .isEqualTo(URI.create("http://0.0.0.0:8080"));
      assertThat(propertiesUtil.getRemoteConnectorsRestApiAddress())
          .isEqualTo(URI.create("http://0.0.0.0:8085"));
      assertThat(propertiesUtil.getRemoteClientGrpcAddress())
          .isEqualTo(URI.create("http://0.0.0.0:8088"));
      assertThat(propertiesUtil.getRemoteClientRestAddress())
          .isEqualTo(URI.create("http://0.0.0.0:8089"));
    }
  }
}
