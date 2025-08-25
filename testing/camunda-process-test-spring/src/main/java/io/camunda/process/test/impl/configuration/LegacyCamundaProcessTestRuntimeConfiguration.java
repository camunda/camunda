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
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "io.camunda.process.test")
@Deprecated
public class LegacyCamundaProcessTestRuntimeConfiguration {

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

  private CamundaProcessTestRuntimeMode runtimeMode = CamundaProcessTestRuntimeMode.MANAGED;

  @Value("camunda-client.request-timeout")
  private Duration camundaClientRequestTimeout =
      CamundaProcessTestRuntimeDefaults.CAMUNDA_CLIENT_REQUEST_TIMEOUT;

  @NestedConfigurationProperty private RemoteConfiguration remote = new RemoteConfiguration();

  public String getCamundaDockerImageName() {
    return camundaDockerImageName;
  }

  public void setCamundaDockerImageName(final String camundaDockerImageName) {
    this.camundaDockerImageName = camundaDockerImageName;
  }

  public String getCamundaDockerImageVersion() {
    return camundaDockerImageVersion;
  }

  public void setCamundaDockerImageVersion(final String camundaDockerImageVersion) {
    this.camundaDockerImageVersion = camundaDockerImageVersion;
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

  public Duration getCamundaClientRequestTimeout() {
    return camundaClientRequestTimeout;
  }

  public void setCamundaClientRequestTimeout(final Duration camundaClientRequestTimeout) {
    this.camundaClientRequestTimeout = camundaClientRequestTimeout;
  }
}
