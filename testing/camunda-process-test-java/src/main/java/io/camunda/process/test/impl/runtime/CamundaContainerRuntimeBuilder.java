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

import io.camunda.process.test.impl.containers.ContainerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CamundaContainerRuntimeBuilder {

  private ContainerFactory containerFactory = new ContainerFactory();

  private String zeebeDockerImageName = ContainerRuntimeDefaults.ZEEBE_DOCKER_IMAGE_NAME;
  private String zeebeDockerImageVersion = ContainerRuntimeDefaults.ZEEBE_DOCKER_IMAGE_VERSION;

  private String elasticsearchDockerImageName =
      ContainerRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_NAME;
  private String elasticsearchDockerImageVersion =
      ContainerRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_VERSION;

  private String operateDockerImageVersion = ContainerRuntimeDefaults.OPERATE_DOCKER_IMAGE_VERSION;

  private String tasklistDockerImageVersion =
      ContainerRuntimeDefaults.TASKLIST_DOCKER_IMAGE_VERSION;

  private String connectorsDockerImageName = ContainerRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_NAME;
  private String connectorsDockerImageVersion =
      ContainerRuntimeDefaults.CONNECTORS_DOCKER_IMAGE_VERSION;

  private final Map<String, String> zeebeEnvVars = new HashMap<>();
  private final Map<String, String> elasticsearchEnvVars = new HashMap<>();
  private final Map<String, String> operateEnvVars = new HashMap<>();
  private final Map<String, String> tasklistEnvVars = new HashMap<>();
  private final Map<String, String> connectorsEnvVars = new HashMap<>();

  private final List<Integer> zeebeExposedPorts = new ArrayList<>();
  private final List<Integer> elasticsearchExposedPorts = new ArrayList<>();
  private final List<Integer> operateExposedPorts = new ArrayList<>();
  private final List<Integer> tasklistExposedPorts = new ArrayList<>();
  private final List<Integer> connectorsExposedPorts = new ArrayList<>();

  private String zeebeLoggerName = ContainerRuntimeDefaults.ZEEBE_LOGGER_NAME;
  private String elasticsearchLoggerName = ContainerRuntimeDefaults.ELASTICSEARCH_LOGGER_NAME;
  private String operateLoggerName = ContainerRuntimeDefaults.OPERATE_LOGGER_NAME;
  private String tasklistLoggerName = ContainerRuntimeDefaults.TASKLIST_LOGGER_NAME;
  private String connectorsLoggerName = ContainerRuntimeDefaults.CONNECTORS_LOGGER_NAME;

  private boolean connectorsEnabled = false;
  private final Map<String, String> connectorsSecrets = new HashMap<>();

  // ============ For testing =================

  CamundaContainerRuntimeBuilder withContainerFactory(final ContainerFactory containerFactory) {
    this.containerFactory = containerFactory;
    return this;
  }

  // ============ Configuration options =================

  public CamundaContainerRuntimeBuilder withZeebeDockerImageName(final String dockerImageName) {
    zeebeDockerImageName = dockerImageName;
    return this;
  }

  public CamundaContainerRuntimeBuilder withZeebeDockerImageVersion(
      final String dockerImageVersion) {
    zeebeDockerImageVersion = dockerImageVersion;
    return this;
  }

  public CamundaContainerRuntimeBuilder withElasticsearchDockerImageName(
      final String dockerImageName) {
    elasticsearchDockerImageName = dockerImageName;
    return this;
  }

  public CamundaContainerRuntimeBuilder withElasticsearchDockerImageVersion(
      final String dockerImageVersion) {
    elasticsearchDockerImageVersion = dockerImageVersion;
    return this;
  }

  public CamundaContainerRuntimeBuilder withOperateDockerImageVersion(
      final String dockerImageVersion) {
    operateDockerImageVersion = dockerImageVersion;
    return this;
  }

  public CamundaContainerRuntimeBuilder withTasklistDockerImageVersion(
      final String dockerImageVersion) {
    tasklistDockerImageVersion = dockerImageVersion;
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsDockerImageName(
      final String dockerImageName) {
    connectorsDockerImageName = dockerImageName;
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsDockerImageVersion(
      final String dockerImageVersion) {
    connectorsDockerImageVersion = dockerImageVersion;
    return this;
  }

  public CamundaContainerRuntimeBuilder withZeebeEnv(final Map<String, String> envVars) {
    zeebeEnvVars.putAll(envVars);
    return this;
  }

  public CamundaContainerRuntimeBuilder withZeebeEnv(final String name, final String value) {
    zeebeEnvVars.put(name, value);
    return this;
  }

  public CamundaContainerRuntimeBuilder withElasticsearchEnv(final Map<String, String> envVars) {
    elasticsearchEnvVars.putAll(envVars);
    return this;
  }

  public CamundaContainerRuntimeBuilder withElasticsearchEnv(
      final String name, final String value) {
    elasticsearchEnvVars.put(name, value);
    return this;
  }

  public CamundaContainerRuntimeBuilder withOperateEnv(final Map<String, String> envVars) {
    operateEnvVars.putAll(envVars);
    return this;
  }

  public CamundaContainerRuntimeBuilder withOperateEnv(final String name, final String value) {
    operateEnvVars.put(name, value);
    return this;
  }

  public CamundaContainerRuntimeBuilder withTasklistEnv(final Map<String, String> envVars) {
    tasklistEnvVars.putAll(envVars);
    return this;
  }

  public CamundaContainerRuntimeBuilder withTasklistEnv(final String name, final String value) {
    tasklistEnvVars.put(name, value);
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsEnv(final Map<String, String> envVars) {
    connectorsEnvVars.putAll(envVars);
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsEnv(final String name, final String value) {
    connectorsEnvVars.put(name, value);
    return this;
  }

  public CamundaContainerRuntimeBuilder withZeebeExposedPort(final int port) {
    zeebeExposedPorts.add(port);
    return this;
  }

  public CamundaContainerRuntimeBuilder withElasticsearchExposedPort(final int port) {
    elasticsearchExposedPorts.add(port);
    return this;
  }

  public CamundaContainerRuntimeBuilder withOperateExposedPort(final int port) {
    operateExposedPorts.add(port);
    return this;
  }

  public CamundaContainerRuntimeBuilder withTasklistExposedPort(final int port) {
    tasklistExposedPorts.add(port);
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsExposedPort(final int port) {
    connectorsExposedPorts.add(port);
    return this;
  }

  public CamundaContainerRuntimeBuilder withZeebeLogger(final String loggerName) {
    zeebeLoggerName = loggerName;
    return this;
  }

  public CamundaContainerRuntimeBuilder withElasticsearchLogger(final String loggerName) {
    elasticsearchLoggerName = loggerName;
    return this;
  }

  public CamundaContainerRuntimeBuilder withOperateLogger(final String loggerName) {
    operateLoggerName = loggerName;
    return this;
  }

  public CamundaContainerRuntimeBuilder withTasklistLogger(final String loggerName) {
    tasklistLoggerName = loggerName;
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsLogger(final String loggerName) {
    connectorsLoggerName = loggerName;
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsEnabled(final boolean enabled) {
    connectorsEnabled = enabled;
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsSecret(
      final String name, final String value) {
    connectorsSecrets.put(name, value);
    return this;
  }

  public CamundaContainerRuntimeBuilder withConnectorsSecrets(final Map<String, String> secrets) {
    connectorsSecrets.putAll(secrets);
    return this;
  }

  // ============ Build =================

  public CamundaContainerRuntime build() {
    return new CamundaContainerRuntime(this, containerFactory);
  }

  // ============ Getters =================

  public String getZeebeDockerImageName() {
    return zeebeDockerImageName;
  }

  public String getZeebeDockerImageVersion() {
    return zeebeDockerImageVersion;
  }

  public String getElasticsearchDockerImageName() {
    return elasticsearchDockerImageName;
  }

  public String getElasticsearchDockerImageVersion() {
    return elasticsearchDockerImageVersion;
  }

  public String getOperateDockerImageVersion() {
    return operateDockerImageVersion;
  }

  public String getTasklistDockerImageVersion() {
    return tasklistDockerImageVersion;
  }

  public String getConnectorsDockerImageName() {
    return connectorsDockerImageName;
  }

  public String getConnectorsDockerImageVersion() {
    return connectorsDockerImageVersion;
  }

  public Map<String, String> getZeebeEnvVars() {
    return zeebeEnvVars;
  }

  public Map<String, String> getElasticsearchEnvVars() {
    return elasticsearchEnvVars;
  }

  public Map<String, String> getOperateEnvVars() {
    return operateEnvVars;
  }

  public Map<String, String> getTasklistEnvVars() {
    return tasklistEnvVars;
  }

  public Map<String, String> getConnectorsEnvVars() {
    return connectorsEnvVars;
  }

  public List<Integer> getZeebeExposedPorts() {
    return zeebeExposedPorts;
  }

  public List<Integer> getElasticsearchExposedPorts() {
    return elasticsearchExposedPorts;
  }

  public List<Integer> getOperateExposedPorts() {
    return operateExposedPorts;
  }

  public List<Integer> getTasklistExposedPorts() {
    return tasklistExposedPorts;
  }

  public List<Integer> getConnectorsExposedPorts() {
    return connectorsExposedPorts;
  }

  public String getZeebeLoggerName() {
    return zeebeLoggerName;
  }

  public String getElasticsearchLoggerName() {
    return elasticsearchLoggerName;
  }

  public String getOperateLoggerName() {
    return operateLoggerName;
  }

  public String getTasklistLoggerName() {
    return tasklistLoggerName;
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
}
