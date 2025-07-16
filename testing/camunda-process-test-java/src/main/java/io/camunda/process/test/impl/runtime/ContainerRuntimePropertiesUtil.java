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

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;

import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.runtime.properties.CamundaContainerRuntimeProperties;
import io.camunda.process.test.impl.runtime.properties.ConnectorsContainerRuntimeProperties;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContainerRuntimePropertiesUtil {

  public static final Logger LOGGER = LoggerFactory.getLogger(ContainerRuntimePropertiesUtil.class);

  public static final String RUNTIME_VERSION_PROPERTIES_FILE =
      "camunda-container-runtime-version.properties";
  public static final String USER_RUNTIME_PROPERTIES_FILE = "camunda-container-runtime.properties";

  public static final String PROPERTY_NAME_RUNTIME_MODE = "runtimeMode";

  public static final String PROPERTY_NAME_ELASTICSEARCH_VERSION = "elasticsearch.version";

  private static final String BASE_DIR = "/";

  private final CamundaContainerRuntimeProperties camundaContainerRuntimeProperties;
  private final ConnectorsContainerRuntimeProperties connectorsContainerRuntimeProperties;
  private final RemoteRuntimeProperties remoteRuntimeProperties;

  private final CamundaProcessTestRuntimeMode runtimeMode;

  private final String elasticsearchVersion;

  public ContainerRuntimePropertiesUtil(final Properties properties) {
    elasticsearchVersion =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_ELASTICSEARCH_VERSION,
            CamundaProcessTestRuntimeDefaults.DEFAULT_ELASTICSEARCH_VERSION);

    camundaContainerRuntimeProperties = new CamundaContainerRuntimeProperties(properties);
    connectorsContainerRuntimeProperties = new ConnectorsContainerRuntimeProperties(properties);
    remoteRuntimeProperties = new RemoteRuntimeProperties(properties);

    runtimeMode =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_RUNTIME_MODE,
            v -> parseRuntimeModeOrDefault(v, CamundaProcessTestRuntimeMode.MANAGED),
            CamundaProcessTestRuntimeMode.MANAGED);
  }

  public static ContainerRuntimePropertiesUtil readProperties() {
    return new ContainerRuntimePropertiesUtil(readPropertiesFileWithUserOverrides(BASE_DIR));
  }

  static ContainerRuntimePropertiesUtil readProperties(final String directoryOverride) {
    return new ContainerRuntimePropertiesUtil(
        readPropertiesFileWithUserOverrides(directoryOverride));
  }

  private static Properties readPropertiesFileWithUserOverrides(final String dir) {
    try (final InputStream versionPropertiesFileStream =
            ContainerRuntimePropertiesUtil.class.getResourceAsStream(
                dir + RUNTIME_VERSION_PROPERTIES_FILE);
        final InputStream userOverridePropertiesFileStream =
            readFileOrNull(dir + USER_RUNTIME_PROPERTIES_FILE)) {

      final Properties properties = new Properties();
      properties.load(versionPropertiesFileStream);

      if (userOverridePropertiesFileStream != null) {
        properties.load(userOverridePropertiesFileStream);
      }

      return properties;

    } catch (final IOException e) {
      LOGGER.warn("Can't read required properties file: {}", RUNTIME_VERSION_PROPERTIES_FILE, e);
    }
    return new Properties();
  }

  private static InputStream readFileOrNull(final String fileName) {
    try {
      return ContainerRuntimePropertiesUtil.class.getResourceAsStream(fileName);
    } catch (final Throwable t) {
      LOGGER.warn("Can't read properties file: {}. Skipping.", fileName, t);
      return null;
    }
  }

  private static CamundaProcessTestRuntimeMode parseRuntimeModeOrDefault(
      final String value, final CamundaProcessTestRuntimeMode defaultValue) {

    try {
      return CamundaProcessTestRuntimeMode.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  public String getCamundaVersion() {
    return camundaContainerRuntimeProperties.getCamundaVersion();
  }

  public String getElasticsearchVersion() {
    return elasticsearchVersion;
  }

  public String getCamundaDockerImageName() {
    return camundaContainerRuntimeProperties.getCamundaDockerImageName();
  }

  public String getCamundaDockerImageVersion() {
    return camundaContainerRuntimeProperties.getCamundaDockerImageVersion();
  }

  public Map<String, String> getCamundaEnvVars() {
    return camundaContainerRuntimeProperties.getCamundaEnvVars();
  }

  public List<Integer> getCamundaExposedPorts() {
    return camundaContainerRuntimeProperties.getCamundaExposedPorts();
  }

  public String getConnectorsDockerImageName() {
    return connectorsContainerRuntimeProperties.getConnectorsDockerImageName();
  }

  public String getConnectorsDockerImageVersion() {
    return connectorsContainerRuntimeProperties.getConnectorsDockerImageVersion();
  }

  public boolean isConnectorsEnabled() {
    return connectorsContainerRuntimeProperties.isConnectorsEnabled();
  }

  public Map<String, String> getConnectorsEnvVars() {
    return connectorsContainerRuntimeProperties.getConnectorsEnvVars();
  }

  public Map<String, String> getConnectorsSecrets() {
    return connectorsContainerRuntimeProperties.getConnectorsSecrets();
  }

  public URI getRemoteCamundaMonitoringApiAddress() {
    return remoteRuntimeProperties.getCamundaMonitoringApiAddress();
  }

  public URI getRemoteConnectorsRestApiAddress() {
    return remoteRuntimeProperties.getConnectorsRestApiAddress();
  }

  public URI getRemoteClientGrpcAddress() {
    return remoteRuntimeProperties.getRemoteClientProperties().getGrpcAddress();
  }

  public URI getRemoteClientRestAddress() {
    return remoteRuntimeProperties.getRemoteClientProperties().getRestAddress();
  }

  public CamundaProcessTestRuntimeMode getRuntimeMode() {
    return runtimeMode;
  }
}
