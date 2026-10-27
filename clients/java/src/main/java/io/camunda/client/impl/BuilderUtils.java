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
package io.camunda.client.impl;

import io.camunda.client.impl.util.Environment;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuilderUtils {

  private static final Logger LOG = LoggerFactory.getLogger(BuilderUtils.class);

  private BuilderUtils() {}

  static void appendProperty(
      final StringBuilder sb, final String propertyName, final Object value) {
    sb.append(propertyName).append(": ").append(value).append("\n");
  }

  /**
   * Applies the provided action to the first non-null property value found in the given list of
   * property names.
   */
  public static void applyPropertyValueIfNotNull(
      final Properties properties, final Consumer<String> action, final String... propertyNames) {
    for (final String propertyName : propertyNames) {
      final Optional<String> value = getPropertyValue(properties, propertyName);
      value.ifPresent(action);
      if (value.isPresent()) {
        break;
      }
    }
  }

  private static Optional<String> getPropertyValue(
      final Properties properties, final String propertyName) {
    return Optional.ofNullable(properties.getProperty(propertyName));
  }

  /**
   * Applies the provided action to the first non-null environment variable value found in the given
   * list of environment variable names.
   */
  public static void applyEnvironmentValueIfNotNull(
      final Consumer<String> action, final String... envNames) {
    for (final String envName : envNames) {
      final Optional<String> value = getEnvironmentVariableValue(envName);
      value.ifPresent(action);
      if (value.isPresent()) {
        break;
      }
    }
  }

  private static Optional<String> getEnvironmentVariableValue(final String envName) {
    return Optional.ofNullable(Environment.system().get(envName));
  }
}
