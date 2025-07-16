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

import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class CamundaProcessTestRuntimeDefaults {

  public static final String DEFAULT_CAMUNDA_DOCKER_IMAGE_NAME = "camunda/camunda";
  public static final String DEFAULT_CAMUNDA_DOCKER_IMAGE_VERSION = "SNAPSHOT";
  public static final String DEFAULT_CONNECTORS_DOCKER_IMAGE_NAME = "camunda/connectors-bundle";
  public static final String DEFAULT_CONNECTORS_DOCKER_IMAGE_VERSION = "SNAPSHOT";
  public static final String DEFAULT_ELASTICSEARCH_VERSION = "8.13.0";

  public static final String ELASTICSEARCH_DOCKER_IMAGE_NAME = "elasticsearch";

  public static final String ELASTICSEARCH_LOGGER_NAME = "tc.elasticsearch";
  public static final String CAMUNDA_LOGGER_NAME = "tc.camunda";
  public static final String CONNECTORS_LOGGER_NAME = "tc.connectors";

  public static final URI LOCAL_CAMUNDA_MONITORING_API_ADDRESS =
      URI.create("http://0.0.0.0:" + ContainerRuntimePorts.CAMUNDA_MONITORING_API);
  public static final URI LOCAL_CONNECTORS_REST_API_ADDRESS = URI.create("http://0.0.0.0:8085");

  private static final ContainerRuntimePropertiesUtil PROPERTIES_UTIL =
      ContainerRuntimePropertiesUtil.readProperties();

  public static final String ELASTICSEARCH_DOCKER_IMAGE_VERSION =
      PROPERTIES_UTIL.getElasticsearchVersion();

  public static final String CAMUNDA_VERSION = PROPERTIES_UTIL.getCamundaVersion();

  public static final String CAMUNDA_DOCKER_IMAGE_NAME =
      PROPERTIES_UTIL.getCamundaDockerImageName();
  public static final String CAMUNDA_DOCKER_IMAGE_VERSION =
      PROPERTIES_UTIL.getCamundaDockerImageVersion();
  public static final Map<String, String> CAMUNDA_ENV_VARS = PROPERTIES_UTIL.getCamundaEnvVars();
  public static final List<Integer> CAMUNDA_EXPOSED_PORTS =
      PROPERTIES_UTIL.getCamundaExposedPorts();

  public static final boolean CONNECTORS_ENABLED = PROPERTIES_UTIL.isConnectorsEnabled();
  public static final String CONNECTORS_DOCKER_IMAGE_NAME =
      PROPERTIES_UTIL.getConnectorsDockerImageName();
  public static final String CONNECTORS_DOCKER_IMAGE_VERSION =
      PROPERTIES_UTIL.getConnectorsDockerImageVersion();
  public static final Map<String, String> CONNECTORS_ENV_VARS =
      PROPERTIES_UTIL.getConnectorsEnvVars();
  public static final Map<String, String> CONNECTORS_SECRETS =
      PROPERTIES_UTIL.getConnectorsSecrets();

  public static final CamundaProcessTestRuntimeMode RUNTIME_MODE = PROPERTIES_UTIL.getRuntimeMode();

  public static final URI REMOTE_CAMUNDA_MONITORING_API_ADDRESS =
      PROPERTIES_UTIL.getRemoteCamundaMonitoringApiAddress();
  public static final URI REMOTE_CONNECTORS_REST_API_ADDRESS =
      PROPERTIES_UTIL.getRemoteConnectorsRestApiAddress();

  public static final URI REMOTE_CLIENT_GRPC_ADDRESS = PROPERTIES_UTIL.getRemoteClientGrpcAddress();
  public static final URI REMOTE_CLIENT_REST_ADDRESS = PROPERTIES_UTIL.getRemoteClientRestAddress();
}
