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
package io.camunda.zeebe.client.impl;

import io.camunda.zeebe.client.impl.util.Environment;
import java.util.Properties;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BuilderUtils {

  private static final Logger LOG = LoggerFactory.getLogger(BuilderUtils.class);

  private BuilderUtils() {}

  static void appendProperty(
      final StringBuilder sb, final String propertyName, final Object value) {
    sb.append(propertyName).append(": ").append(value).append("\n");
  }

  static void applyIfNotNull(
      final Properties properties,
      final String propertyName,
      final String legacyPropertyName,
      final Consumer<String> action) {
    final String value = getOrLegacy(properties, propertyName, legacyPropertyName);
    if (value != null) {
      action.accept(value);
    }
  }

  static String getOrLegacy(
      final Properties properties, final String propertyName, final String legacyPropertyName) {
    if (properties.containsKey(propertyName)) {
      return properties.getProperty(propertyName);
    }
    if (properties.containsKey(legacyPropertyName)) {
      LOG.warn("{} is deprecated. Use {} instead", legacyPropertyName, propertyName);
      return properties.getProperty(legacyPropertyName);
    }
    return null;
  }

  static void applyIfNotNull(
      final String envName, final String legacyEnvName, final Consumer<String> action) {
    final String value = getOrLegacy(Environment.system(), envName, legacyEnvName);
    if (value != null) {
      action.accept(value);
    }
  }

  static String getOrLegacy(
      final Environment environment, final String envName, final String legacyEnvName) {
    if (environment.isDefined(envName)) {
      return environment.get(envName);
    }
    if (environment.isDefined(legacyEnvName)) {
      LOG.warn("{} is deprecated. Use {} instead", legacyEnvName, envName);
      return environment.get(legacyEnvName);
    }
    return null;
  }
}
