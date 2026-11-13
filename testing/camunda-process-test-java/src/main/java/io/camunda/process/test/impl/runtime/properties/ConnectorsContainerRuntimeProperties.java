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
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.process.test.impl.runtime.util.VersionedPropertiesUtil;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ConnectorsContainerRuntimeProperties {
  public static final String PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME =
      "connectorsDockerImageName";
  public static final String PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION =
      "connectorsDockerImageVersion";
  public static final String PROPERTY_NAME_CONNECTORS_ENABLED = "connectorsEnabled";
  public static final String PROPERTY_NAME_CONNECTORS_ENV_VARS_PREFIX = "connectorsEnvVars";
  public static final String PROPERTY_NAME_CONNECTORS_SECRETS_PREFIX = "connectorsSecrets";
  public static final String PROPERTY_NAME_CONNECTORS_EXPOSED_PORTS_PREFIX =
      "connectorsExposedPorts";
  public static final String PROPERTY_NAME_MANAGED_SCRIPTS_ENABLED = "managedScriptsEnabled";
  public static final String PROPERTY_NAME_MANAGED_SCRIPTS_PYTHON_VERSION =
      "managedScriptsPythonVersion";
  public static final String PROPERTY_NAME_MANAGED_SCRIPTS_NODE_VERSION =
      "managedScriptsNodeVersion";

  private final boolean isConnectorsEnabled;
  private final String connectorsDockerImageName;
  private final String connectorsDockerImageVersion;
  private final Map<String, String> connectorsEnvVars;
  private final Map<String, String> connectorsSecrets;
  private final List<Integer> connectorsExposedPorts;
  private final boolean isManagedScriptsEnabled;
  private final String managedScriptsPythonVersion;
  private final String managedScriptsNodeVersion;

  public ConnectorsContainerRuntimeProperties(
      final Properties properties, final VersionedPropertiesUtil versionedPropertiesReader) {

    // connectors are disabled by default
    isConnectorsEnabled =
        getPropertyOrDefault(properties, PROPERTY_NAME_CONNECTORS_ENABLED, "false")
            .trim()
            .equalsIgnoreCase("true");
    connectorsDockerImageName =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME,
            CamundaProcessTestRuntimeDefaults.DEFAULT_CONNECTORS_DOCKER_IMAGE_NAME);
    connectorsDockerImageVersion =
        versionedPropertiesReader.getVersion(
            properties,
            PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
            CamundaProcessTestRuntimeDefaults.DEFAULT_CONNECTORS_DOCKER_IMAGE_VERSION);
    connectorsEnvVars = getPropertyMapOrEmpty(properties, PROPERTY_NAME_CONNECTORS_ENV_VARS_PREFIX);
    connectorsSecrets = getPropertyMapOrEmpty(properties, PROPERTY_NAME_CONNECTORS_SECRETS_PREFIX);
    connectorsExposedPorts =
        getPropertyListOrEmpty(
            properties, PROPERTY_NAME_CONNECTORS_EXPOSED_PORTS_PREFIX, Integer::parseInt);

    // managed scripts are disabled by default; no version means the latest of the image
    isManagedScriptsEnabled =
        getPropertyOrDefault(properties, PROPERTY_NAME_MANAGED_SCRIPTS_ENABLED, "false")
            .trim()
            .equalsIgnoreCase("true");
    managedScriptsPythonVersion =
        getPropertyOrNull(properties, PROPERTY_NAME_MANAGED_SCRIPTS_PYTHON_VERSION);
    managedScriptsNodeVersion =
        getPropertyOrNull(properties, PROPERTY_NAME_MANAGED_SCRIPTS_NODE_VERSION);
  }

  public boolean isConnectorsEnabled() {
    return isConnectorsEnabled;
  }

  public String getConnectorsDockerImageName() {
    return connectorsDockerImageName;
  }

  public String getConnectorsDockerImageVersion() {
    return connectorsDockerImageVersion;
  }

  public Map<String, String> getConnectorsEnvVars() {
    return connectorsEnvVars;
  }

  public Map<String, String> getConnectorsSecrets() {
    return connectorsSecrets;
  }

  public List<Integer> getConnectorsExposedPorts() {
    return connectorsExposedPorts;
  }

  public boolean isManagedScriptsEnabled() {
    return isManagedScriptsEnabled;
  }

  public String getManagedScriptsPythonVersion() {
    return managedScriptsPythonVersion;
  }

  public String getManagedScriptsNodeVersion() {
    return managedScriptsNodeVersion;
  }
}
