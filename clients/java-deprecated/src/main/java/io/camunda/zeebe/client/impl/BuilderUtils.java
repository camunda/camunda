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
      final Properties properties, final String propertyName, final Consumer<String> action) {
    final String value = getProperty(properties, propertyName);
    if (value != null) {
      action.accept(value);
    }
  }

  static String getProperty(final Properties properties, final String propertyName) {
    if (properties.containsKey(propertyName)) {
      return properties.getProperty(propertyName);
    }
    return null;
  }

  static void applyIfNotNull(final String envName, final Consumer<String> action) {
    final String value = getProperty(Environment.system(), envName);
    if (value != null) {
      action.accept(value);
    }
  }

  static String getProperty(final Environment environment, final String envName) {
    if (environment.isDefined(envName)) {
      return environment.get(envName);
    }
    return null;
  }
}
