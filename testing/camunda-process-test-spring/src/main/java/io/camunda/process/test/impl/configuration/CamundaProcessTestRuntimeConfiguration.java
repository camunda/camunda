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
package io.camunda.process.test.impl.configuration;

import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.spring.client.properties.CamundaClientProperties;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "io.camunda.process.test")
public class CamundaProcessTestRuntimeConfiguration {

  private String camundaDockerImageName =
      CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME;
  private String camundaDockerImageVersion =
      CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION;
  private Map<String, String> camundaEnvVars = Collections.emptyMap();
  private List<Integer> camundaExposedPorts = Collections.emptyList();

  private boolean connectorsEnabled = false;
  private String connectorsDockerImageName =
      CamundaProcessTestRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_NAME;
  private String connectorsDockerImageVersion =
      CamundaProcessTestRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_VERSION;
  private Map<String, String> connectorsEnvVars = Collections.emptyMap();
  private Map<String, String> connectorsSecrets = Collections.emptyMap();
  private List<Integer> connectorsExposedPorts = Collections.emptyList();

  private String camundaLoggerName = CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_LOGGER_NAME;
  private String connectorsLoggerName =
      CamundaProcessTestRuntimeDefaults.DEFAULT_CONNECTORS_LOGGER_NAME;

  private CamundaProcessTestRuntimeMode runtimeMode = CamundaProcessTestRuntimeMode.MANAGED;

  @NestedConfigurationProperty private RemoteConfiguration remote = new RemoteConfiguration();

  /**
   * Gets the Camunda docker image version.
   *
   * @return the camunda docker image version
   * @deprecated use getCamundaDockerImageVersion
   * @since 8.8.0
   */
  @Deprecated
  public String getCamundaVersion() {
    return camundaDockerImageVersion;
  }

  /**
   * Sets the Camunda docker image version.
   *
   * @param camundaDockerImageVersion the Camunda docker image version to set
   * @deprecated use setCamundaDockerImageVersion
   * @since 8.8.0
   */
  @Deprecated
  public void setCamundaVersion(final String camundaDockerImageVersion) {
    this.camundaDockerImageVersion = camundaDockerImageVersion;
  }

  /**
   * Gets the Camunda docker image version.
   *
   * @return the camunda docker image version
   */
  public String getCamundaDockerImageVersion() {
    return camundaDockerImageVersion;
  }

  /**
   * Sets the Camunda docker image version.
   *
   * @param camundaDockerImageVersion the Camunda docker image version to set
   */
  public void setCamundaDockerImageVersion(final String camundaDockerImageVersion) {
    this.camundaDockerImageVersion = camundaDockerImageVersion;
  }

  public String getCamundaDockerImageName() {
    return camundaDockerImageName;
  }

  public void setCamundaDockerImageName(final String camundaDockerImageName) {
    this.camundaDockerImageName = camundaDockerImageName;
  }

  public Map<String, String> getCamundaEnvVars() {
    return camundaEnvVars;
  }

  public void setCamundaEnvVars(final Map<String, String> camundaEnvVars) {
    this.camundaEnvVars = camundaEnvVars;
  }

  public List<Integer> getCamundaExposedPorts() {
    return camundaExposedPorts;
  }

  public void setCamundaExposedPorts(final List<Integer> camundaExposedPorts) {
    this.camundaExposedPorts = camundaExposedPorts;
  }

  public boolean isConnectorsEnabled() {
    return connectorsEnabled;
  }

  public void setConnectorsEnabled(final boolean connectorsEnabled) {
    this.connectorsEnabled = connectorsEnabled;
  }

  public String getConnectorsDockerImageName() {
    return connectorsDockerImageName;
  }

  public void setConnectorsDockerImageName(final String connectorsDockerImageName) {
    this.connectorsDockerImageName = connectorsDockerImageName;
  }

  public String getConnectorsDockerImageVersion() {
    return connectorsDockerImageVersion;
  }

  public void setConnectorsDockerImageVersion(final String connectorsDockerImageVersion) {
    this.connectorsDockerImageVersion = connectorsDockerImageVersion;
  }

  public Map<String, String> getConnectorsEnvVars() {
    return connectorsEnvVars;
  }

  public void setConnectorsEnvVars(final Map<String, String> connectorsEnvVars) {
    this.connectorsEnvVars = connectorsEnvVars;
  }

  public Map<String, String> getConnectorsSecrets() {
    return connectorsSecrets;
  }

  public void setConnectorsSecrets(final Map<String, String> connectorsSecrets) {
    this.connectorsSecrets = connectorsSecrets;
  }

  public List<Integer> getConnectorsExposedPorts() {
    return connectorsExposedPorts;
  }

  public void setConnectorsExposedPorts(final List<Integer> connectorsExposedPorts) {
    this.connectorsExposedPorts = connectorsExposedPorts;
  }

  public String getCamundaLoggerName() {
    return camundaLoggerName;
  }

  public void setCamundaLoggerName(final String camundaLoggerName) {
    this.camundaLoggerName = camundaLoggerName;
  }

  public String getConnectorsLoggerName() {
    return connectorsLoggerName;
  }

  public void setConnectorsLoggerName(final String connectorsLoggerName) {
    this.connectorsLoggerName = connectorsLoggerName;
  }

  public CamundaProcessTestRuntimeMode getRuntimeMode() {
    return runtimeMode;
  }

  public void setRuntimeMode(final CamundaProcessTestRuntimeMode runtimeMode) {
    this.runtimeMode = runtimeMode;
  }

  public RemoteConfiguration getRemote() {
    return remote;
  }

  public void setRemote(final RemoteConfiguration remote) {
    this.remote = remote;
  }

  public static class RemoteConfiguration {

    @NestedConfigurationProperty
    private CamundaClientProperties client = new CamundaClientProperties();

    private URI camundaMonitoringApiAddress =
        CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS;

    private URI connectorsRestApiAddress =
        CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS;

    public CamundaClientProperties getClient() {
      return client;
    }

    public void setClient(final CamundaClientProperties client) {
      this.client = client;
    }

    public URI getCamundaMonitoringApiAddress() {
      return camundaMonitoringApiAddress;
    }

    public void setCamundaMonitoringApiAddress(final URI camundaMonitoringApiAddress) {
      this.camundaMonitoringApiAddress = camundaMonitoringApiAddress;
    }

    public URI getConnectorsRestApiAddress() {
      return connectorsRestApiAddress;
    }

    public void setConnectorsRestApiAddress(final URI connectorsRestApiAddress) {
      this.connectorsRestApiAddress = connectorsRestApiAddress;
    }
  }
}
