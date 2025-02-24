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

import io.camunda.process.test.impl.runtime.ContainerRuntimeDefaults;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "io.camunda.process.test")
public class CamundaContainerRuntimeConfiguration {

  private String camundaVersion = ContainerRuntimeDefaults.CAMUNDA_VERSION;
  private String camundaDockerImageName = ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME;
  private Map<String, String> camundaEnvVars = Collections.emptyMap();
  private List<Integer> camundaExposedPorts = Collections.emptyList();

  private boolean connectorsEnabled = false;
  private String connectorsDockerImageName = ContainerRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_NAME;
  private String connectorsDockerImageVersion =
      ContainerRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_VERSION;
  private Map<String, String> connectorsEnvVars = Collections.emptyMap();
  private Map<String, String> connectorsSecrets = Collections.emptyMap();
  private Map<String, String> elasticsearchEnvVars = Collections.emptyMap();

  public String getCamundaVersion() {
    return camundaVersion;
  }

  public void setCamundaVersion(final String camundaVersion) {
    this.camundaVersion = camundaVersion;
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

  public Map<String, String> getElasticsearchEnvVars() {
    return elasticsearchEnvVars;
  }

  public void setElasticsearchEnvVars(final Map<String, String> elasticsearchEnvVars) {
    this.elasticsearchEnvVars = elasticsearchEnvVars;
  }
}
