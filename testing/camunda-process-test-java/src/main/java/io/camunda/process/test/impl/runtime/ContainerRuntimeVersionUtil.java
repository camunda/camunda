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

public final class ContainerRuntimeVersionUtil {

  public static final Logger LOGGER = LoggerFactory.getLogger(ContainerRuntimeVersionUtil.class);

  public static final String VERSION_PROPERTIES_FILE = "/camunda-container-runtime.properties";

  public static final String PROPERTY_NAME_CAMUNDA_VERSION = "camunda.version";
  public static final String PROPERTY_NAME_ELASTICSEARCH_VERSION = "elasticsearch.version";

  public static final String CAMUNDA_VERSION_SNAPSHOT = "SNAPSHOT";
  public static final String ELASTICSEARCH_VERSION_DEFAULT = "8.13.0";

  private static final Pattern SEMANTIC_VERSION_PATTERN =
      Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(-.*)?");

  private static final String VERSION_FORMAT = "%d.%d.%d";

  private final String camundaVersion;
  private final String elasticsearchVersion;

  public ContainerRuntimeVersionUtil(final Properties versionProperties) {
    camundaVersion = resolveCamundaVersion(versionProperties);
    elasticsearchVersion = resolveElasticsearchVersion(versionProperties);
  }

  private static String resolveCamundaVersion(final Properties versionProperties) {
    return Optional.ofNullable(versionProperties.getProperty(PROPERTY_NAME_CAMUNDA_VERSION))
        .map(SEMANTIC_VERSION_PATTERN::matcher)
        .filter(Matcher::find)
        .map(
            matcher -> {
              final int major = Integer.parseInt(matcher.group(1));
              final int minor = Integer.parseInt(matcher.group(2));
              final int patch = Integer.parseInt(matcher.group(3));
              final String label = matcher.group(4);
              return resolveCamundaVersion(major, minor, patch, label);
            })
        .orElse(CAMUNDA_VERSION_SNAPSHOT);
  }

  private static String resolveCamundaVersion(
      final int major, final int minor, final int patch, final String label) {

    if (label == null) {
      // release version
      return String.format(VERSION_FORMAT, major, minor, patch);

    } else if (patch == 0) {
      // current dev version
      return CAMUNDA_VERSION_SNAPSHOT;

    } else {
      // maintenance dev version
      final int previousPatchVersion = patch - 1;
      return String.format(VERSION_FORMAT, major, minor, previousPatchVersion);
    }
  }

  private String resolveElasticsearchVersion(final Properties versionProperties) {
    return Optional.ofNullable(versionProperties.getProperty(PROPERTY_NAME_ELASTICSEARCH_VERSION))
        .filter(version -> SEMANTIC_VERSION_PATTERN.matcher(version).find())
        .orElse(ELASTICSEARCH_VERSION_DEFAULT);
  }

  public static ContainerRuntimeVersionUtil readVersions() {
    return new ContainerRuntimeVersionUtil(readVersionFile());
  }

  private static Properties readVersionFile() {
    try (final InputStream versionFileStream =
        ContainerRuntimeVersionUtil.class.getResourceAsStream(VERSION_PROPERTIES_FILE)) {

      final Properties properties = new Properties();
      properties.load(versionFileStream);
      return properties;

    } catch (final IOException e) {
      LOGGER.warn(String.format("Can't read version file: %s", VERSION_PROPERTIES_FILE), e);
    }
    return new Properties();
  }

  public String getCamundaVersion() {
    return camundaVersion;
  }

  public String getElasticsearchVersion() {
    return elasticsearchVersion;
  }
}
