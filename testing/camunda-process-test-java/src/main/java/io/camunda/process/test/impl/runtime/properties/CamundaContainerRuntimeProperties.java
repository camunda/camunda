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
package io.camunda.process.test.impl.runtime.properties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyListOrEmpty;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyMapOrEmpty;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.process.test.impl.runtime.util.VersionedPropertiesUtil;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class CamundaContainerRuntimeProperties {
  public static final String PROPERTY_NAME_CAMUNDA_VERSION = "camunda.version";
  public static final String PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME = "camundaDockerImageName";
  public static final String PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION =
      "camundaDockerImageVersion";
  public static final String PROPERTY_NAME_CAMUNDA_ENV_VARS_PREFIX = "camundaEnvVars";
  public static final String PROPERTY_NAME_CAMUNDA_EXPOSED_PORTS_PREFIX = "camundaExposedPorts";
  public static final String PROPERTY_NAME_CAMUNDA_LOGGER_NAME = "camundaLoggerName";
  public static final String PROPERTY_NAME_CONNECTORS_LOGGER_NAME = "connectorsLoggerName";

  private final String camundaDockerImageName;
  private final String camundaDockerImageVersion;
  private final Map<String, String> camundaEnvVars;
  private final List<Integer> camundaExposedPorts;

  private final String camundaLoggerName;
  private final String connectorsLoggerName;

  public CamundaContainerRuntimeProperties(
      final Properties properties, final VersionedPropertiesUtil versionedPropertiesReader) {
    camundaDockerImageName =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME,
            CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_DOCKER_IMAGE_NAME);
    camundaDockerImageVersion =
        versionedPropertiesReader.getVersion(
            properties,
            PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION,
            CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_DOCKER_IMAGE_VERSION);
    camundaEnvVars = getPropertyMapOrEmpty(properties, PROPERTY_NAME_CAMUNDA_ENV_VARS_PREFIX);
    camundaExposedPorts =
        getPropertyListOrEmpty(
            properties, PROPERTY_NAME_CAMUNDA_EXPOSED_PORTS_PREFIX, Integer::parseInt);
    camundaLoggerName =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CAMUNDA_LOGGER_NAME,
            CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_LOGGER_NAME);
    connectorsLoggerName =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CONNECTORS_LOGGER_NAME,
            CamundaProcessTestRuntimeDefaults.DEFAULT_CONNECTORS_LOGGER_NAME);
  }

  public String getCamundaDockerImageName() {
    return camundaDockerImageName;
  }

  public String getCamundaDockerImageVersion() {
    return camundaDockerImageVersion;
  }

  public Map<String, String> getCamundaEnvVars() {
    return camundaEnvVars;
  }

  public List<Integer> getCamundaExposedPorts() {
    return camundaExposedPorts;
  }

  public String getCamundaLoggerName() {
    return camundaLoggerName;
  }

  public String getConnectorsLoggerName() {
    return connectorsLoggerName;
  }
}
