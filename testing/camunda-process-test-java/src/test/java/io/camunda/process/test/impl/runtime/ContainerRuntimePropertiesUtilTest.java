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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.runtime.properties.CamundaClientWorkerProperties;
import io.camunda.process.test.impl.runtime.properties.CamundaContainerRuntimeProperties;
import io.camunda.process.test.impl.runtime.properties.CamundaProcessTestClientProperties;
import io.camunda.process.test.impl.runtime.properties.ConnectorsContainerRuntimeProperties;
import io.camunda.process.test.impl.runtime.properties.CoverageReportProperties;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientAuthProperties;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientAuthProperties.AuthMethod;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientCloudProperties;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientProperties.ClientMode;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
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

  private final GitPropertiesUtil emptyGitProperties = new GitPropertiesUtil(new Properties());

  @Test
  void shouldReturnDefaults() {
    // given
    final Properties properties = new Properties();

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties, emptyGitProperties);

    // then
    assertThat(propertiesUtil.getElasticsearchVersion()).isEqualTo("8.13.0");
    assertThat(propertiesUtil.getCamundaDockerImageName()).isEqualTo("camunda/camunda");
    assertThat(propertiesUtil.getCamundaDockerImageVersion()).isEqualTo("SNAPSHOT");
    assertThat(propertiesUtil.getConnectorsDockerImageName())
        .isEqualTo("camunda/connectors-bundle");
    assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo("SNAPSHOT");

    assertThat(propertiesUtil.getCoverageReportProperties().getCoverageReportDirectory())
        .isEqualTo("target/coverage-report");

    assertThat(propertiesUtil.getRemoteRuntimeConnectionTimeout()).isEqualTo(Duration.ofMinutes(1));
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
        new ContainerRuntimePropertiesUtil(properties, emptyGitProperties);

    // then
    assertThat(propertiesUtil.getElasticsearchVersion()).isEqualTo("8.13.0");
    assertThat(propertiesUtil.getCamundaDockerImageName()).isEqualTo("camunda/camunda");
    assertThat(propertiesUtil.getCamundaDockerImageVersion()).isEqualTo("SNAPSHOT");
    assertThat(propertiesUtil.getConnectorsDockerImageName())
        .isEqualTo("camunda/connectors-bundle");
    assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo("SNAPSHOT");
    assertThat(propertiesUtil.getRemoteRuntimeProperties().getRemoteClientProperties().getMode())
        .isEqualTo(ClientMode.selfManaged);
    assertThat(
            propertiesUtil
                .getRemoteRuntimeProperties()
                .getRemoteClientProperties()
                .getAuthProperties()
                .getMethod())
        .isEqualTo(AuthMethod.none);
  }

  @ParameterizedTest
  @CsvSource({
    "main, SNAPSHOT",
    "stable/8.8, 8.8-SNAPSHOT",
    "backport-123-to-stable/8.8, 8.8-SNAPSHOT",
    " , SNAPSHOT",
    "feature-123, SNAPSHOT"
  })
  void shouldReturnDefaultVersionsBasedOnGitBranch(
      final String branchName, final String expectedVersion) {
    // given
    final Properties properties = new Properties();

    final Properties gitProperties = new Properties();
    if (branchName != null) {
      gitProperties.put(GitPropertiesUtil.PROPERTY_NAME_GIT_BRANCH, branchName);
    }

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties, new GitPropertiesUtil(gitProperties));

    // then
    assertThat(propertiesUtil.getCamundaDockerImageVersion()).isEqualTo(expectedVersion);
    assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo(expectedVersion);
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
        new ContainerRuntimePropertiesUtil(properties, emptyGitProperties);

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
        new ContainerRuntimePropertiesUtil(properties, emptyGitProperties);

    // then
    assertThat(propertiesUtil.getCamundaDockerImageName()).isEqualTo(expectedName);
  }

  @ParameterizedTest
  @CsvSource({
    // minor releases
    "8.8.0, main, 8.8.0",
    "8.8.0, stable/8.8, 8.8.0",
    "8.8.0, backport-123-to-stable/8.8, 8.8.0",
    "8.8.0, , 8.8.0",
    // patch releases
    "8.8.1, main, 8.8.1",
    "8.8.1, stable/8.8, 8.8.1",
    "8.8.1, backport-123-to-stable/8.8, 8.8.1",
    "8.8.1, , 8.8.1",
    // SNAPSHOT versions
    "8.9.0-SNAPSHOT, main, SNAPSHOT",
    "8.9.0-SNAPSHOT, stable/8.8, 8.8-SNAPSHOT",
    "8.9.0-SNAPSHOT, backport-123-to-stable/8.8, 8.8-SNAPSHOT",
    "8.9.0-SNAPSHOT, , SNAPSHOT",
    "8.8.1-SNAPSHOT, main, SNAPSHOT",
    "8.8.1-SNAPSHOT, stable/8.8, 8.8-SNAPSHOT",
    "8.8.1-SNAPSHOT, backport-123-to-stable/8.8, 8.8-SNAPSHOT",
    "8.8.1-SNAPSHOT, , SNAPSHOT",
    "8.8.2-SNAPSHOT, main, SNAPSHOT",
    "8.8.2-SNAPSHOT, stable/8.8, 8.8-SNAPSHOT",
    "8.8.2-SNAPSHOT, backport-123-to-stable/8.8, 8.8-SNAPSHOT",
    "8.8.2-SNAPSHOT, , SNAPSHOT",
    // rc/alpha versions
    "8.8.0-rc1, main, 8.8.0-rc1",
    "8.8.0-rc1, stable/8.8, 8.8.0-rc1",
    "8.8.0-rc1, backport-123-to-stable/8.8, 8.8.0-rc1",
    "8.8.0-rc1, , 8.8.0-rc1",
    "8.8.0-alpha1, main, 8.8.0-alpha1",
    "8.8.0-alpha1, stable/8.8, 8.8.0-alpha1",
    "8.8.0-alpha1, backport-123-to-stable/8.8, 8.8.0-alpha1",
    "8.8.0-alpha1, , 8.8.0-alpha1",
    "8.8.0-alpha1.1, main, 8.8.0-alpha1.1",
    "8.8.0-alpha1.1, stable/8.8, 8.8.0-alpha1.1",
    "8.8.0-alpha1.1, backport-123-to-stable/8.8, 8.8.0-alpha1.1",
    "8.8.0-alpha1.1, , 8.8.0-alpha1.1",
    "8.8.0-alpha1-rc1, main, 8.8.0-alpha1-rc1",
    "8.8.0-alpha1-rc1, stable/8.8, 8.8.0-alpha1-rc1",
    "8.8.0-alpha1-rc1, backport-123-to-stable/8.8, 8.8.0-alpha1-rc1",
    "8.8.0-alpha1-rc1, , 8.8.0-alpha1-rc1",
    // custom versions
    "8.8.0-optimize, main, 8.8.0-optimize",
    "8.8.0-optimize, stable/8.8, 8.8.0-optimize",
    "8.8.0-optimize, backport-123-to-stable/8.8, 8.8.0-optimize",
    "8.8.0-optimize, , 8.8.0-optimize",
    "custom-version, main, custom-version",
    "custom-version, stable/8.8, custom-version",
    "custom-version, backport-123-to-stable/8.8, custom-version",
    "custom-version, , custom-version",
  })
  void shouldReturnCamundaDockerImageVersion(
      final String propertyVersion, final String branchName, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(
        CamundaContainerRuntimeProperties.PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION,
        propertyVersion);

    final Properties gitProperties = new Properties();
    if (branchName != null) {
      gitProperties.put(GitPropertiesUtil.PROPERTY_NAME_GIT_BRANCH, branchName);
    }

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties, new GitPropertiesUtil(gitProperties));

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
        new ContainerRuntimePropertiesUtil(properties, emptyGitProperties);

    // then
    assertThat(propertiesUtil.getConnectorsDockerImageName()).isEqualTo(expectedName);
  }

  @ParameterizedTest
  @CsvSource({
    // minor releases
    "8.8.0, main, 8.8.0",
    "8.8.0, stable/8.8, 8.8.0",
    "8.8.0, backport-123-to-stable/8.8, 8.8.0",
    "8.8.0, , 8.8.0",
    // patch releases
    "8.8.1, main, 8.8.1",
    "8.8.1, stable/8.8, 8.8.1",
    "8.8.1, backport-123-to-stable/8.8, 8.8.1",
    "8.8.1, , 8.8.1",
    // SNAPSHOT versions
    "8.9.0-SNAPSHOT, main, SNAPSHOT",
    "8.9.0-SNAPSHOT, stable/8.8, 8.8-SNAPSHOT",
    "8.9.0-SNAPSHOT, backport-123-to-stable/8.8, 8.8-SNAPSHOT",
    "8.9.0-SNAPSHOT, , SNAPSHOT",
    "8.8.1-SNAPSHOT, main, SNAPSHOT",
    "8.8.1-SNAPSHOT, stable/8.8, 8.8-SNAPSHOT",
    "8.8.1-SNAPSHOT, backport-123-to-stable/8.8, 8.8-SNAPSHOT",
    "8.8.1-SNAPSHOT, , SNAPSHOT",
    "8.8.2-SNAPSHOT, main, SNAPSHOT",
    "8.8.2-SNAPSHOT, stable/8.8, 8.8-SNAPSHOT",
    "8.8.2-SNAPSHOT, backport-123-to-stable/8.8, 8.8-SNAPSHOT",
    "8.8.2-SNAPSHOT, , SNAPSHOT",
    // rc/alpha versions
    "8.8.0-rc1, main, 8.8.0-rc1",
    "8.8.0-rc1, stable/8.8, 8.8.0-rc1",
    "8.8.0-rc1, backport-123-to-stable/8.8, 8.8.0-rc1",
    "8.8.0-rc1, , 8.8.0-rc1",
    "8.8.0-alpha1, main, 8.8.0-alpha1",
    "8.8.0-alpha1, stable/8.8, 8.8.0-alpha1",
    "8.8.0-alpha1, backport-123-to-stable/8.8, 8.8.0-alpha1",
    "8.8.0-alpha1, , 8.8.0-alpha1",
    "8.8.0-alpha1.1, main, 8.8.0-alpha1.1",
    "8.8.0-alpha1.1, stable/8.8, 8.8.0-alpha1.1",
    "8.8.0-alpha1.1, backport-123-to-stable/8.8, 8.8.0-alpha1.1",
    "8.8.0-alpha1.1, , 8.8.0-alpha1.1",
    "8.8.0-alpha1-rc1, main, 8.8.0-alpha1-rc1",
    "8.8.0-alpha1-rc1, stable/8.8, 8.8.0-alpha1-rc1",
    "8.8.0-alpha1-rc1, backport-123-to-stable/8.8, 8.8.0-alpha1-rc1",
    "8.8.0-alpha1-rc1, , 8.8.0-alpha1-rc1",
    // custom versions
    "8.8.0-optimize, main, 8.8.0-optimize",
    "8.8.0-optimize, stable/8.8, 8.8.0-optimize",
    "8.8.0-optimize, backport-123-to-stable/8.8, 8.8.0-optimize",
    "8.8.0-optimize, , 8.8.0-optimize",
    "custom-version, main, custom-version",
    "custom-version, stable/8.8, custom-version",
    "custom-version, backport-123-to-stable/8.8, custom-version",
    "custom-version, , custom-version",
  })
  void shouldReturnConnectorsDockerImageVersion(
      final String propertyVersion, final String branchName, final String expectedVersion) {
    // given
    final Properties properties = new Properties();
    properties.put(
        ConnectorsContainerRuntimeProperties.PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
        propertyVersion);

    final Properties gitProperties = new Properties();
    if (branchName != null) {
      gitProperties.put(GitPropertiesUtil.PROPERTY_NAME_GIT_BRANCH, branchName);
    }

    // when
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties, new GitPropertiesUtil(gitProperties));

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
        new ContainerRuntimePropertiesUtil(properties, emptyGitProperties);

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
        new ContainerRuntimePropertiesUtil(properties, emptyGitProperties);

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
          ContainerRuntimePropertiesUtil.readProperties(
              "/containerRuntimePropertiesUtil/", emptyGitProperties);

      // then
      assertThat(propertiesUtil.getElasticsearchVersion()).isEqualTo("1.1.0");
      assertThat(propertiesUtil.getCamundaDockerImageName()).isEqualTo("camunda/custom-camunda");
      assertThat(propertiesUtil.getCamundaDockerImageVersion()).isEqualTo("8.8.0-rc1");
      assertThat(propertiesUtil.getCamundaLoggerName()).isEqualTo("camunda.custom.logger");
      assertThat(propertiesUtil.getConnectorsLoggerName()).isEqualTo("connectors.custom.logger");
      assertThat(propertiesUtil.getConnectorsDockerImageName())
          .isEqualTo("camunda/connectors-bundle");
      assertThat(propertiesUtil.getConnectorsDockerImageVersion()).isEqualTo("8.8.3");

      final RemoteRuntimeClientCloudProperties cloudProps =
          propertiesUtil
              .getRemoteRuntimeProperties()
              .getRemoteClientProperties()
              .getCloudProperties();
      assertThat(cloudProps.getClusterId()).isEqualTo("clusterId");
      assertThat(cloudProps.getRegion()).isEqualTo("region");

      final RemoteRuntimeClientAuthProperties authProps =
          propertiesUtil
              .getRemoteRuntimeProperties()
              .getRemoteClientProperties()
              .getAuthProperties();

      assertThat(authProps.getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(authProps.getUsername()).isEqualTo("username");
      assertThat(authProps.getPassword()).isEqualTo("password");
      assertThat(authProps.getClientId()).isEqualTo("clientId");
      assertThat(authProps.getClientSecret()).isEqualTo("clientSecret");
      assertThat(authProps.getTokenUrl()).isEqualTo(URI.create("http://example.com"));
      assertThat(authProps.getAudience()).isEqualTo("audience");
      assertThat(authProps.getScope()).isEqualTo("scope");
      assertThat(authProps.getResource()).isEqualTo("resource");
      assertThat(authProps.getKeystorePath()).isEqualTo(Paths.get("/path/to/keystore"));
      assertThat(authProps.getKeystorePassword()).isEqualTo("keystorePassword");
      assertThat(authProps.getKeystoreKeyPassword()).isEqualTo("keystoreKeyPassword");
      assertThat(authProps.getTruststorePath()).isEqualTo(Paths.get("/path/to/truststore"));
      assertThat(authProps.getTruststorePassword()).isEqualTo("truststorePassword");
      assertThat(authProps.getCredentialsCachePath()).isEqualTo("/path/to/credentialsCache");
      assertThat(authProps.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
      assertThat(authProps.getReadTimeout()).isEqualTo(Duration.ofSeconds(6));
      assertThat(authProps.getCredentialsCachePath()).isEqualTo("/path/to/credentialsCache");
      assertThat(authProps.getClientAssertionKeystorePath())
          .isEqualTo(Paths.get("/path/to/assertion/keystore"));
      assertThat(authProps.getClientAssertionKeystorePassword()).isEqualTo("keystorePassword");
      assertThat(authProps.getClientAssertionKeystoreKeyAlias()).isEqualTo("keystoreKeyAlias");
      assertThat(authProps.getClientAssertionKeystoreKeyPassword())
          .isEqualTo("keystoreKeyPassword");
    }

    @Test
    public void shouldHaveCustomConfigurationParams() {
      final ContainerRuntimePropertiesUtil propertiesUtil =
          ContainerRuntimePropertiesUtil.readProperties(
              "/containerRuntimePropertiesUtil/", emptyGitProperties);

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
      assertThat(propertiesUtil.getConnectorsExposedPorts())
          .containsExactlyInAnyOrder(9080, 9081, 9088);

      assertThat(propertiesUtil.getRuntimeMode()).isEqualTo(CamundaProcessTestRuntimeMode.REMOTE);
      assertThat(propertiesUtil.getRemoteCamundaMonitoringApiAddress())
          .isEqualTo(URI.create("http://0.0.0.0:8080"));
      assertThat(propertiesUtil.getRemoteConnectorsRestApiAddress())
          .isEqualTo(URI.create("http://0.0.0.0:8085"));
      assertThat(propertiesUtil.getRemoteClientGrpcAddress())
          .isEqualTo(URI.create("http://0.0.0.0:8088"));
      assertThat(propertiesUtil.getRemoteClientRestAddress())
          .isEqualTo(URI.create("http://0.0.0.0:8089"));
      assertThat(propertiesUtil.getRemoteRuntimeConnectionTimeout())
          .isEqualTo(Duration.ofSeconds(30));

      assertThat(propertiesUtil.isMultiTenancyEnabled()).isTrue();

      final CoverageReportProperties coverageReportProperties =
          propertiesUtil.getCoverageReportProperties();
      assertThat(coverageReportProperties.getCoverageReportDirectory())
          .isEqualTo("custom/coverage-report");
      assertThat(coverageReportProperties.getCoverageExcludedProcesses())
          .containsExactlyInAnyOrder("process1", "process2");
    }

    @Test
    public void shouldHaveCustomClientProperties() {
      // when
      final ContainerRuntimePropertiesUtil propertiesUtil =
          ContainerRuntimePropertiesUtil.readProperties(
              "/containerRuntimePropertiesUtil/", emptyGitProperties);

      final CamundaProcessTestClientProperties clientProps =
          propertiesUtil.getCamundaClientProperties();

      // then
      assertThat(clientProps.getRestAddress()).isEqualTo(URI.create("http://0.0.0.0:8090"));
      assertThat(clientProps.getGrpcAddress()).isEqualTo(URI.create("http://0.0.0.0:8091"));
      assertThat(clientProps.getRequestTimeout()).hasSeconds(60);
      assertThat(clientProps.getRequestTimeoutOffset()).hasSeconds(59);
      assertThat(clientProps.getTenantId()).isEqualTo("customTenantId");
      assertThat(clientProps.getMessageTimeToLive()).hasSeconds(58);
      assertThat(clientProps.getMaxMessageSize()).isEqualTo(1000);
      assertThat(clientProps.getMaxMetadataSize()).isEqualTo(2000);
      assertThat(clientProps.getExecutionThreads()).isEqualTo(8);
      assertThat(clientProps.getCaCertificatePath()).isEqualTo("/path/to/file/");
      assertThat(clientProps.getKeepAlive()).hasSeconds(57);
      assertThat(clientProps.getOverrideAuthority()).isEqualTo("customOverrideAuthority");
      assertThat(clientProps.getPreferRestOverGrpc()).isFalse();

      final CamundaClientWorkerProperties clientWorkerProps = clientProps.getClientWorkerProps();

      assertThat(clientWorkerProps.getPollInterval()).hasHours(1);
      assertThat(clientWorkerProps.getTimeout()).hasHours(2);
      assertThat(clientWorkerProps.getMaxJobsActive()).isEqualTo(10);
      assertThat(clientWorkerProps.getName()).isEqualTo("customWorkerName");
      assertThat(clientWorkerProps.getTenantIds()).contains("customTenantA", "customTenantB");
      assertThat(clientWorkerProps.getStreamEnabled()).isTrue();

      final CamundaClientConfiguration clientBuilder =
          propertiesUtil.getCamundaClientBuilderFactory().get().build().getConfiguration();

      assertThat(clientBuilder.getRestAddress()).isEqualTo(URI.create("http://0.0.0.0:8090"));
      assertThat(clientBuilder.getGrpcAddress()).isEqualTo(URI.create("http://0.0.0.0:8091"));
      assertThat(clientBuilder.getDefaultRequestTimeout()).hasSeconds(60);
      assertThat(clientBuilder.getDefaultRequestTimeoutOffset()).hasSeconds(59);
      // The tenantId is not updated when in RuntimeMode.REMOTE
      // assertThat(clientBuilder.getDefaultTenantId()).isEqualTo("customTenantId");
      assertThat(clientBuilder.getDefaultMessageTimeToLive()).hasSeconds(58);
      assertThat(clientBuilder.getMaxMessageSize()).isEqualTo(1000);
      assertThat(clientBuilder.getMaxMetadataSize()).isEqualTo(2000);
      assertThat(clientBuilder.getNumJobWorkerExecutionThreads()).isEqualTo(8);
      assertThat(clientBuilder.getKeepAlive()).hasSeconds(57);
      assertThat(clientBuilder.getOverrideAuthority()).isEqualTo("customOverrideAuthority");
      assertThat(clientBuilder.getCaCertificatePath()).isEqualTo("/path/to/file/");

      assertThat(clientBuilder.getDefaultJobPollInterval()).hasHours(1);
      assertThat(clientBuilder.getDefaultJobTimeout()).hasHours(2);
      assertThat(clientBuilder.getDefaultJobWorkerMaxJobsActive()).isEqualTo(10);
      assertThat(clientBuilder.getDefaultJobWorkerName()).isEqualTo("customWorkerName");
      // The tenantIds are not assigned when in RuntimeMode.REMOTE
      // assertThat(clientBuilder.getDefaultJobWorkerTenantIds()).containsExactlyInAnyOrder("customTenantA", "customTenantB");
      assertThat(clientBuilder.getDefaultJobWorkerStreamEnabled()).isTrue();
    }
  }
}
