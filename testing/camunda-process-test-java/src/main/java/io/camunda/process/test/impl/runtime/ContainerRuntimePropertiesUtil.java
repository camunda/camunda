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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContainerRuntimePropertiesUtil {

  public static final Logger LOGGER = LoggerFactory.getLogger(ContainerRuntimePropertiesUtil.class);

  public static final String RUNTIME_PROPERTIES_FILE = "/camunda-container-runtime.properties";

  public static final String PROPERTY_NAME_CAMUNDA_VERSION = "camunda.version";
  public static final String PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME = "camunda.dockerImageName";
  public static final String PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION =
      "camunda.dockerImageVersion";
  public static final String PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME =
      "connectors.dockerImageName";
  public static final String PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION =
      "connectors.dockerImageVersion";
  public static final String PROPERTY_NAME_ELASTICSEARCH_VERSION = "elasticsearch.version";

  public static final String SNAPSHOT_VERSION = "SNAPSHOT";

  private static final Pattern SEMANTIC_VERSION_PATTERN =
      Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(-.*)?");

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{.*}");

  private static final String VERSION_FORMAT = "%d.%d.%d";

  private final String camundaVersion;
  private final String camundaDockerImageName;
  private final String camundaDockerImageVersion;
  private final String connectorsDockerImageName;
  private final String connectorsDockerImageVersion;
  private final String elasticsearchVersion;

  public ContainerRuntimePropertiesUtil(final Properties properties) {
    camundaVersion =
        getLatestReleasedVersion(
            properties,
            PROPERTY_NAME_CAMUNDA_VERSION,
            ContainerRuntimeDefaults.DEFAULT_CAMUNDA_DOCKER_IMAGE_VERSION);
    elasticsearchVersion =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_ELASTICSEARCH_VERSION,
            ContainerRuntimeDefaults.DEFAULT_ELASTICSEARCH_VERSION);

    camundaDockerImageName =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_NAME,
            ContainerRuntimeDefaults.DEFAULT_CAMUNDA_DOCKER_IMAGE_NAME);
    camundaDockerImageVersion =
        getLatestReleasedVersion(
            properties,
            PROPERTY_NAME_CAMUNDA_DOCKER_IMAGE_VERSION,
            ContainerRuntimeDefaults.DEFAULT_CAMUNDA_DOCKER_IMAGE_VERSION);

    connectorsDockerImageName =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_NAME,
            ContainerRuntimeDefaults.DEFAULT_CONNECTORS_DOCKER_IMAGE_NAME);
    connectorsDockerImageVersion =
        getLatestReleasedVersion(
            properties,
            PROPERTY_NAME_CONNECTORS_DOCKER_IMAGE_VERSION,
            ContainerRuntimeDefaults.DEFAULT_CONNECTORS_DOCKER_IMAGE_VERSION);
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
      final Properties versionProperties, final String propertyName, final String defaultValue) {
    final String propertyValue = versionProperties.getProperty(propertyName);
    if (propertyValue == null || isPlaceholder(propertyValue)) {
      return defaultValue;

    } else {
      return propertyValue;
    }
  }

  public static ContainerRuntimePropertiesUtil readProperties() {
    return new ContainerRuntimePropertiesUtil(readPropertiesFile());
  }

  private static Properties readPropertiesFile() {
    try (final InputStream propertiesFileStream =
        ContainerRuntimePropertiesUtil.class.getResourceAsStream(RUNTIME_PROPERTIES_FILE)) {

      final Properties properties = new Properties();
      properties.load(propertiesFileStream);
      return properties;

    } catch (final IOException e) {
      LOGGER.warn("Can't read properties file: {}", RUNTIME_PROPERTIES_FILE, e);
    }
    return new Properties();
  }

  public String getCamundaVersion() {
    return camundaVersion;
  }

  public String getElasticsearchVersion() {
    return elasticsearchVersion;
  }

  public String getCamundaDockerImageName() {
    return camundaDockerImageName;
  }

  public String getCamundaDockerImageVersion() {
    return camundaDockerImageVersion;
  }

  public String getConnectorsDockerImageName() {
    return connectorsDockerImageName;
  }

  public String getConnectorsDockerImageVersion() {
    return connectorsDockerImageVersion;
  }
}
