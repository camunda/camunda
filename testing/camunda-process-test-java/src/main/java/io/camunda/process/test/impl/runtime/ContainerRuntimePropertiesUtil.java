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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContainerRuntimePropertiesUtil {

  public static final Logger LOGGER = LoggerFactory.getLogger(ContainerRuntimePropertiesUtil.class);

  public static final String RUNTIME_VERSION_PROPERTIES_FILE =
      "/camunda-container-runtime-version.properties";
  public static final String USER_RUNTIME_PROPERTIES_FILE = "/camunda-container-runtime.properties";

  public static final String PROPERTY_NAME_CAMUNDA_VERSION = "camunda.version";
  public static final String PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME = "camunda.dockerImageName";
  public static final String PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION =
      "camunda.dockerImageVersion";
  public static final String PROPERTY_NAME_CAMUNDA_ENV_VARS_PREFIX = "camundaEnvVars.";
  public static final String PROPERTY_NAME_CAMUNDA_EXPOSED_PORTS_PREFIX = "camundaExposedPorts.";

  public static final String PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME =
      "connectors.dockerImageName";
  public static final String PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION =
      "connectors.dockerImageVersion";
  public static final String PROPERTY_NAME_CONNECTORS_ENABLED = "connectorsEnabled";
  public static final String PROPERTY_NAME_CONNECTORS_ENV_VARS_PREFIX = "connectorsEnvVars.";
  public static final String PROPERTY_NAME_CONNECTORS_SECRETS_PREFIX = "connectorsSecrets.";

  public static final String PROPERTY_NAME_RUNTIME_MODE = "runtimeMode";

  public static final String PROPERTY_NAME_REMOTE_CAMUNDA_MONITORING_API_ADDRESS =
      "remote.camundaMonitoringApiAddress";
  public static final String PROPERTY_NAME_REMOTE_CONNECTORS_REST_API_ADDRESS =
      "remote.connectorsRestApiAddress";

  public static final String PROPERTY_NAME_REMOTE_CLIENT_GRPC_ADDRESS = "remote.client.grpcAddress";
  public static final String PROPERTY_NAME_REMOTE_CLIENT_REST_ADDRESS = "remote.client.restAddress";

  public static final String PROPERTY_NAME_ELASTICSEARCH_VERSION = "elasticsearch.version";

  public static final String SNAPSHOT_VERSION = "SNAPSHOT";
  private static final Pattern SEMANTIC_VERSION_PATTERN =
      Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(-.*)?");
  private static final String VERSION_FORMAT = "%d.%d.%d";

  /**
   * Format string for versions that include additional labels (e.g., alpha, rc). This is used to
   * format semantic versions with non-SNAPSHOT labels.
   */
  private static final String LABELED_VERSION_FORMAT = "%d.%d.%d%s";

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{.*}");

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
            v -> CamundaProcessTestRuntimeMode.tryValueOf(v, CamundaProcessTestRuntimeMode.MANAGED),
            CamundaProcessTestRuntimeMode.MANAGED);
  }

  private static String getLatestReleasedVersion(
      final Properties properties, final String propertyName, final String defaultValue) {

    final String propertyValue = properties.getProperty(propertyName);
    if (propertyValue == null || isPlaceholder(propertyValue)) {
      return defaultValue;
    }

    return Optional.of(propertyValue)
        .map(SEMANTIC_VERSION_PATTERN::matcher)
        .filter(Matcher::find)
        .map(
            matcher -> {
              final int major = Integer.parseInt(matcher.group(1));
              final int minor = Integer.parseInt(matcher.group(2));
              final int patch = Integer.parseInt(matcher.group(3));
              final String label = matcher.group(4);
              return getLatestReleasedVersion(major, minor, patch, label);
            })
        .orElse(propertyValue);
  }

  private static boolean isPlaceholder(final String propertyValue) {
    return PLACEHOLDER_PATTERN.matcher(propertyValue).matches();
  }

  private static String getLatestReleasedVersion(
      final int major, final int minor, final int patch, final String label) {

    if (label == null) {
      // release version
      return String.format(VERSION_FORMAT, major, minor, patch);
    } else if (!label.contains(SNAPSHOT_VERSION)) {
      // alpha, rc or other labeled version
      return String.format(LABELED_VERSION_FORMAT, major, minor, patch, label);
    } else if (patch == 0) {
      // current dev version
      return SNAPSHOT_VERSION;
    } else {
      // maintenance dev version
      final int previousPatchVersion = patch - 1;
      return String.format(VERSION_FORMAT, major, minor, previousPatchVersion);
    }
  }

  private static String getPropertyOrDefault(
      final Properties properties, final String propertyName, final String defaultValue) {
    final String propertyValue = properties.getProperty(propertyName);
    if (propertyValue == null || isPlaceholder(propertyValue)) {
      return defaultValue;

    } else {
      return propertyValue;
    }
  }

  private static <T> T getPropertyOrDefault(
      final Properties properties,
      final String propertyName,
      final Function<String, T> converter,
      final T defaultValue) {

    final String propertyValue = properties.getProperty(propertyName);
    if (propertyValue == null || isPlaceholder(propertyValue)) {
      return defaultValue;
    } else {
      return converter.apply(propertyValue);
    }
  }

  private static Map<String, String> getPropertyMapOrEmpty(
      final Properties properties, final String propertyNamePrefix) {

    return properties.stringPropertyNames().stream()
        .filter(key -> key.startsWith(propertyNamePrefix))
        .collect(
            Collectors.toMap(
                key -> key.substring(propertyNamePrefix.length()),
                key -> readProperty(properties, key).trim()));
  }

  private static <T> List<T> getPropertyListOrEmpty(
      final Properties properties,
      final String propertyNamePrefix,
      final Function<String, T> converter) {

    return properties.stringPropertyNames().stream()
        .filter(key -> key.startsWith(propertyNamePrefix))
        .map(key -> readProperty(properties, key).trim())
        .map(converter)
        .collect(Collectors.toList());
  }

  private static String readProperty(final Properties properties, final String key) {
    return properties.getProperty(key);
  }

  public static ContainerRuntimePropertiesUtil readProperties() {
    return new ContainerRuntimePropertiesUtil(readPropertiesFileWithUserOverrides());
  }

  private static Properties readPropertiesFileWithUserOverrides() {
    try (final InputStream versionPropertiesFileStream =
            ContainerRuntimePropertiesUtil.class.getResourceAsStream(
                RUNTIME_VERSION_PROPERTIES_FILE);
        final InputStream userOverridePropertiesFileStream =
            safeFileRead(USER_RUNTIME_PROPERTIES_FILE)) {

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

  private static InputStream safeFileRead(final String fileName) {
    try {
      return ContainerRuntimePropertiesUtil.class.getResourceAsStream(fileName);
    } catch (final Throwable t) {
      LOGGER.warn("Can't read properties file: {}. Skipping.", fileName, t);
      return null;
    }
  }

  private static <T> T safeConversion(String value, Function<String, T> converter, T defaultValue) {
    try {
      return converter.apply(value);
    } catch (final Throwable t) {
      return defaultValue;
    }
  }

  public String getCamundaVersion() {
    return camundaContainerRuntimeProperties.camundaVersion;
  }

  public String getElasticsearchVersion() {
    return elasticsearchVersion;
  }

  public String getCamundaDockerImageName() {
    return camundaContainerRuntimeProperties.camundaDockerImageName;
  }

  public String getCamundaDockerImageVersion() {
    return camundaContainerRuntimeProperties.camundaDockerImageVersion;
  }

  public Map<String, String> getCamundaEnvVars() {
    return camundaContainerRuntimeProperties.camundaEnvVars;
  }

  public List<Integer> getCamundaExposedPorts() {
    return camundaContainerRuntimeProperties.camundaExposedPorts;
  }

  public String getConnectorsDockerImageName() {
    return connectorsContainerRuntimeProperties.connectorsDockerImageName;
  }

  public String getConnectorsDockerImageVersion() {
    return connectorsContainerRuntimeProperties.connectorsDockerImageVersion;
  }

  public boolean isConnectorsEnabled() {
    return connectorsContainerRuntimeProperties.isConnectorsEnabled;
  }

  public Map<String, String> getConnectorsEnvVars() {
    return connectorsContainerRuntimeProperties.connectorsEnvVars;
  }

  public Map<String, String> getConnectorsSecrets() {
    return connectorsContainerRuntimeProperties.connectorsSecrets;
  }

  public URI getRemoteCamundaMonitoringApiAddress() {
    return remoteRuntimeProperties.camundaMonitoringApiAddress;
  }

  public URI getRemoteConnectorsRestApiAddress() {
    return remoteRuntimeProperties.connectorsRestApiAddress;
  }

  public URI getRemoteClientGrpcAddress() {
    return remoteRuntimeProperties.remoteClientProperties.grpcAddress;
  }

  public URI getRemoteClientRestAddress() {
    return remoteRuntimeProperties.remoteClientProperties.restAddress;
  }

  public CamundaProcessTestRuntimeMode getRuntimeMode() {
    return runtimeMode;
  }

  private static class CamundaContainerRuntimeProperties {
    private final String camundaVersion;
    private final String camundaDockerImageName;
    private final String camundaDockerImageVersion;
    private final Map<String, String> camundaEnvVars;
    private final List<Integer> camundaExposedPorts;

    public CamundaContainerRuntimeProperties(final Properties properties) {
      camundaVersion =
          getLatestReleasedVersion(
              properties,
              PROPERTY_NAME_CAMUNDA_VERSION,
              CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_DOCKER_IMAGE_VERSION);
      camundaDockerImageName =
          getPropertyOrDefault(
              properties,
              PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME,
              CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_DOCKER_IMAGE_NAME);
      camundaDockerImageVersion =
          getLatestReleasedVersion(
              properties,
              PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION,
              CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_DOCKER_IMAGE_VERSION);
      camundaEnvVars = getPropertyMapOrEmpty(properties, PROPERTY_NAME_CAMUNDA_ENV_VARS_PREFIX);
      camundaExposedPorts =
          getPropertyListOrEmpty(
              properties, PROPERTY_NAME_CAMUNDA_EXPOSED_PORTS_PREFIX, Integer::parseInt);
    }
  }

  private static class ConnectorsContainerRuntimeProperties {
    private final boolean isConnectorsEnabled;
    private final String connectorsDockerImageName;
    private final String connectorsDockerImageVersion;
    private final Map<String, String> connectorsEnvVars;
    private final Map<String, String> connectorsSecrets;

    public ConnectorsContainerRuntimeProperties(final Properties properties) {
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
          getLatestReleasedVersion(
              properties,
              PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
              CamundaProcessTestRuntimeDefaults.DEFAULT_CONNECTORS_DOCKER_IMAGE_VERSION);
      connectorsEnvVars =
          getPropertyMapOrEmpty(properties, PROPERTY_NAME_CONNECTORS_ENV_VARS_PREFIX);
      connectorsSecrets =
          getPropertyMapOrEmpty(properties, PROPERTY_NAME_CONNECTORS_SECRETS_PREFIX);
    }
  }

  private static class RemoteRuntimeProperties {
    private final URI camundaMonitoringApiAddress;
    private final URI connectorsRestApiAddress;

    private final RemoteRuntimeClientProperties remoteClientProperties;

    public RemoteRuntimeProperties(final Properties properties) {
      camundaMonitoringApiAddress =
          getPropertyOrDefault(
              properties,
              PROPERTY_NAME_REMOTE_CAMUNDA_MONITORING_API_ADDRESS,
              v ->
                  safeConversion(
                      v,
                      URI::create,
                      CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS),
              CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS);

      connectorsRestApiAddress =
          getPropertyOrDefault(
              properties,
              PROPERTY_NAME_REMOTE_CONNECTORS_REST_API_ADDRESS,
              v ->
                  safeConversion(
                      v,
                      URI::create,
                      CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS),
              CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS);

      remoteClientProperties = new RemoteRuntimeClientProperties(properties);
    }
  }

  private static class RemoteRuntimeClientProperties {
    private final URI grpcAddress;
    private final URI restAddress;

    public RemoteRuntimeClientProperties(final Properties properties) {
      grpcAddress =
          getPropertyOrDefault(
              properties,
              PROPERTY_NAME_REMOTE_CLIENT_GRPC_ADDRESS,
              v ->
                  safeConversion(
                      v,
                      URI::create,
                      CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS),
              null);

      restAddress =
          getPropertyOrDefault(
              properties,
              PROPERTY_NAME_REMOTE_CLIENT_REST_ADDRESS,
              v ->
                  safeConversion(
                      v,
                      URI::create,
                      CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS),
              null);
    }
  }
}
