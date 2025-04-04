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

import java.util.Properties;
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
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CAMUNDA_VERSION, "${project.version}");
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_ELASTICSEARCH_VERSION,
        "${version.elasticsearch}");
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME,
        "${io.camunda.process.test.camundaDockerImageName}");
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION,
        "${io.camunda.process.test.camundaDockerImageVersion}");
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME,
        "camunda/connectors-bundle");
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
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
    "8.5.2-rc1, 8.5.1",
    "custom-version, custom-version"
  })
  void shouldReturnCamundaVersion(final String propertyVersion, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(ContainerRuntimePropertiesUtil.PROPERTY_NAME_CAMUNDA_VERSION, propertyVersion);

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
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME, propertyName);

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
    "8.5.2-rc1, 8.5.1",
    "custom-version, custom-version"
  })
  void shouldReturnCamundaDockerImageVersion(
      final String propertyVersion, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION, propertyVersion);

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
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME, propertyName);

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
    "8.5.2-rc1, 8.5.1",
    "custom-version, custom-version"
  })
  void shouldReturnConnectorsDockerImageVersion(
      final String propertyVersion, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(
        ContainerRuntimePropertiesUtil.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
        propertyVersion);

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties);

    // then
    assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo(expectedVersion);
  }
}
