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

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestGlobalRuntime;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.containers.ContainerFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CamundaProcessTestRuntimeBuilder {

  private ContainerFactory containerFactory = new ContainerFactory();

  private String camundaDockerImageName =
      CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME;
  private String camundaDockerImageVersion =
      CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION;

  private String elasticsearchDockerImageName =
      CamundaProcessTestRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_NAME;
  private String elasticsearchDockerImageVersion =
      CamundaProcessTestRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_VERSION;

  private String connectorsDockerImageName =
      CamundaProcessTestRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_NAME;
  private String connectorsDockerImageVersion =
      CamundaProcessTestRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_VERSION;

  private final Map<String, String> camundaEnvVars = new HashMap<>();
  private final Map<String, String> elasticsearchEnvVars = new HashMap<>();
  private final Map<String, String> connectorsEnvVars = new HashMap<>();

  private final List<Integer> camundaExposedPorts = new ArrayList<>();
  private final List<Integer> elasticsearchExposedPorts = new ArrayList<>();
  private final List<Integer> connectorsExposedPorts = new ArrayList<>();

  private String camundaLoggerName = CamundaProcessTestRuntimeDefaults.CAMUNDA_LOGGER_NAME;
  private String elasticsearchLoggerName =
      CamundaProcessTestRuntimeDefaults.ELASTICSEARCH_LOGGER_NAME;
  private String connectorsLoggerName = CamundaProcessTestRuntimeDefaults.CONNECTORS_LOGGER_NAME;

  private boolean connectorsEnabled = false;
  private final Map<String, String> connectorsSecrets = new HashMap<>();

  private final boolean isGlobalRuntimeDisabled =
      CamundaProcessTestRuntimeDefaults.GLOBAL_CPT_RUNTIME_DISABLED;
  private boolean forceLocalRuntime = false;
  private CamundaProcessTestRuntimeMode runtimeMode = CamundaProcessTestRuntimeMode.MANAGED;

  private CamundaClientBuilderFactory remoteCamundaClientBuilderFactory =
      () -> CamundaClient.newClientBuilder().usePlaintext();
  private URI remoteCamundaMonitoringApiAddress =
      CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS;
  private URI remoteConnectorsRestApiAddress =
      CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS;

  // ============ For testing =================

  CamundaProcessTestRuntimeBuilder withContainerFactory(final ContainerFactory containerFactory) {
    this.containerFactory = containerFactory;
    return this;
  }

  // ============ Configuration options =================

  public CamundaProcessTestRuntimeBuilder withCamundaDockerImageName(final String dockerImageName) {
    camundaDockerImageName = dockerImageName;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withCamundaDockerImageVersion(
      final String dockerImageVersion) {
    camundaDockerImageVersion = dockerImageVersion;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withElasticsearchDockerImageName(
      final String dockerImageName) {
    elasticsearchDockerImageName = dockerImageName;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withElasticsearchDockerImageVersion(
      final String dockerImageVersion) {
    elasticsearchDockerImageVersion = dockerImageVersion;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsDockerImageName(
      final String dockerImageName) {
    connectorsDockerImageName = dockerImageName;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsDockerImageVersion(
      final String dockerImageVersion) {
    connectorsDockerImageVersion = dockerImageVersion;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withCamundaEnv(final Map<String, String> envVars) {
    camundaEnvVars.putAll(envVars);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withCamundaEnv(final String name, final String value) {
    camundaEnvVars.put(name, value);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withElasticsearchEnv(final Map<String, String> envVars) {
    elasticsearchEnvVars.putAll(envVars);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withElasticsearchEnv(
      final String name, final String value) {
    elasticsearchEnvVars.put(name, value);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsEnv(final Map<String, String> envVars) {
    connectorsEnvVars.putAll(envVars);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsEnv(final String name, final String value) {
    connectorsEnvVars.put(name, value);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withCamundaExposedPort(final int port) {
    camundaExposedPorts.add(port);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withElasticsearchExposedPort(final int port) {
    elasticsearchExposedPorts.add(port);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsExposedPort(final int port) {
    connectorsExposedPorts.add(port);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withCamundaLogger(final String loggerName) {
    camundaLoggerName = loggerName;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withElasticsearchLogger(final String loggerName) {
    elasticsearchLoggerName = loggerName;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsLogger(final String loggerName) {
    connectorsLoggerName = loggerName;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsEnabled(final boolean enabled) {
    connectorsEnabled = enabled;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsSecret(
      final String name, final String value) {
    connectorsSecrets.put(name, value);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withConnectorsSecrets(final Map<String, String> secrets) {
    connectorsSecrets.putAll(secrets);
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withRuntimeMode(
      final CamundaProcessTestRuntimeMode runtimeMode) {
    this.runtimeMode = runtimeMode;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withLocalRuntime() {
    this.forceLocalRuntime = true;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withRemoteCamundaClientBuilderFactory(
      final CamundaClientBuilderFactory remoteCamundaClientBuilderFactory) {
    this.remoteCamundaClientBuilderFactory = remoteCamundaClientBuilderFactory;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withRemoteCamundaMonitoringApiAddress(
      final URI remoteCamundaMonitoringApiAddress) {
    this.remoteCamundaMonitoringApiAddress = remoteCamundaMonitoringApiAddress;
    return this;
  }

  public CamundaProcessTestRuntimeBuilder withRemoteConnectorsRestApiAddress(
      final URI remoteConnectorsRestApiAddress) {
    this.remoteConnectorsRestApiAddress = remoteConnectorsRestApiAddress;
    return this;
  }

  // ============ Build =================

  public CamundaProcessTestRuntime build() {
    if (shouldUseGlobalRuntime()) {
      System.out.println("Using the global runtime!");
      return CamundaProcessTestGlobalRuntime.INSTANCE.getRuntime();
    }

    System.out.println("Using a local runtime");
    return buildRuntime();
  }

  private CamundaProcessTestRuntime buildRuntime() {
    if (runtimeMode == CamundaProcessTestRuntimeMode.REMOTE) {
      return new CamundaProcessTestRemoteRuntime(this);
    }

    return new CamundaProcessTestContainerRuntime(this, containerFactory);
  }

  // ============ Getters =================

  public String getCamundaDockerImageName() {
    return camundaDockerImageName;
  }

  public String getCamundaDockerImageVersion() {
    return camundaDockerImageVersion;
  }

  public String getElasticsearchDockerImageName() {
    return elasticsearchDockerImageName;
  }

  public String getElasticsearchDockerImageVersion() {
    return elasticsearchDockerImageVersion;
  }

  public String getConnectorsDockerImageName() {
    return connectorsDockerImageName;
  }

  public String getConnectorsDockerImageVersion() {
    return connectorsDockerImageVersion;
  }

  public Map<String, String> getCamundaEnvVars() {
    return camundaEnvVars;
  }

  public Map<String, String> getElasticsearchEnvVars() {
    return elasticsearchEnvVars;
  }

  public Map<String, String> getConnectorsEnvVars() {
    return connectorsEnvVars;
  }

  public List<Integer> getCamundaExposedPorts() {
    return camundaExposedPorts;
  }

  public List<Integer> getElasticsearchExposedPorts() {
    return elasticsearchExposedPorts;
  }

  public List<Integer> getConnectorsExposedPorts() {
    return connectorsExposedPorts;
  }

  public String getCamundaLoggerName() {
    return camundaLoggerName;
  }

  public String getElasticsearchLoggerName() {
    return elasticsearchLoggerName;
  }

  public String getConnectorsLoggerName() {
    return connectorsLoggerName;
  }

  public boolean isConnectorsEnabled() {
    return connectorsEnabled;
  }

  public Map<String, String> getConnectorsSecrets() {
    return connectorsSecrets;
  }

  public CamundaProcessTestRuntimeMode getRuntimeMode() {
    return runtimeMode;
  }

  public boolean isForceLocalRuntime() {
    return forceLocalRuntime;
  }

  public CamundaClientBuilderFactory getRemoteCamundaClientBuilderFactory() {
    return remoteCamundaClientBuilderFactory;
  }

  public URI getRemoteCamundaMonitoringApiAddress() {
    return remoteCamundaMonitoringApiAddress;
  }

  public URI getRemoteConnectorsRestApiAddress() {
    return remoteConnectorsRestApiAddress;
  }

  // ============ Global Runtime =================

  private boolean shouldUseGlobalRuntime() {
    return !isGlobalRuntimeDisabled
        && !forceLocalRuntime
        && hasCompatibleConfiguration(CamundaProcessTestGlobalRuntime.INSTANCE.getRuntimeBuilder());
  }

  private boolean hasCompatibleConfiguration(final CamundaProcessTestRuntimeBuilder other) {
    return connectorsEnabled == other.connectorsEnabled
        && Objects.equals(camundaDockerImageName, other.camundaDockerImageName)
        && Objects.equals(camundaDockerImageVersion, other.camundaDockerImageVersion)
        && Objects.equals(elasticsearchDockerImageName, other.elasticsearchDockerImageName)
        && Objects.equals(elasticsearchDockerImageVersion, other.elasticsearchDockerImageVersion)
        && Objects.equals(connectorsDockerImageName, other.connectorsDockerImageName)
        && Objects.equals(connectorsDockerImageVersion, other.connectorsDockerImageVersion)
        && Objects.equals(camundaEnvVars, other.camundaEnvVars)
        && Objects.equals(elasticsearchEnvVars, other.elasticsearchEnvVars)
        && Objects.equals(connectorsEnvVars, other.connectorsEnvVars)
        && Objects.equals(camundaExposedPorts, other.camundaExposedPorts)
        && Objects.equals(elasticsearchExposedPorts, other.elasticsearchExposedPorts)
        && Objects.equals(connectorsExposedPorts, other.connectorsExposedPorts)
        && Objects.equals(camundaLoggerName, other.camundaLoggerName)
        && Objects.equals(elasticsearchLoggerName, other.elasticsearchLoggerName)
        && Objects.equals(connectorsLoggerName, other.connectorsLoggerName)
        && Objects.equals(connectorsSecrets, other.connectorsSecrets)
        && runtimeMode == other.runtimeMode
        && Objects.equals(
            remoteCamundaClientBuilderFactory, other.remoteCamundaClientBuilderFactory)
        && Objects.equals(
            remoteCamundaMonitoringApiAddress, other.remoteCamundaMonitoringApiAddress)
        && Objects.equals(remoteConnectorsRestApiAddress, other.remoteConnectorsRestApiAddress);
  }

  @Override
  public String toString() {
    return "CamundaProcessTestRuntimeBuilder{"
        + "containerFactory="
        + containerFactory
        + ", camundaDockerImageName='"
        + camundaDockerImageName
        + '\''
        + ", camundaDockerImageVersion='"
        + camundaDockerImageVersion
        + '\''
        + ", elasticsearchDockerImageName='"
        + elasticsearchDockerImageName
        + '\''
        + ", elasticsearchDockerImageVersion='"
        + elasticsearchDockerImageVersion
        + '\''
        + ", connectorsDockerImageName='"
        + connectorsDockerImageName
        + '\''
        + ", connectorsDockerImageVersion='"
        + connectorsDockerImageVersion
        + '\''
        + ", camundaEnvVars="
        + camundaEnvVars
        + ", elasticsearchEnvVars="
        + elasticsearchEnvVars
        + ", connectorsEnvVars="
        + connectorsEnvVars
        + ", camundaExposedPorts="
        + camundaExposedPorts
        + ", elasticsearchExposedPorts="
        + elasticsearchExposedPorts
        + ", connectorsExposedPorts="
        + connectorsExposedPorts
        + ", camundaLoggerName='"
        + camundaLoggerName
        + '\''
        + ", elasticsearchLoggerName='"
        + elasticsearchLoggerName
        + '\''
        + ", connectorsLoggerName='"
        + connectorsLoggerName
        + '\''
        + ", connectorsEnabled="
        + connectorsEnabled
        + ", connectorsSecrets="
        + connectorsSecrets
        + ", isGlobalRuntimeDisabled="
        + isGlobalRuntimeDisabled
        + ", forceLocalRuntime="
        + forceLocalRuntime
        + ", runtimeMode="
        + runtimeMode
        + ", remoteCamundaClientBuilderFactory="
        + remoteCamundaClientBuilderFactory
        + ", remoteCamundaMonitoringApiAddress="
        + remoteCamundaMonitoringApiAddress
        + ", remoteConnectorsRestApiAddress="
        + remoteConnectorsRestApiAddress
        + '}';
  }
}
