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
package io.camunda.process.test.impl.runtime.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Convenience methods for extracting data from a Properties file. May include singular values, map
 * and lists.
 */
public class PropertiesUtil {
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{.*}");

  /**
   * Reads a property and returns null if it doesn't exist or the value is a placeholder. Falls back
   * to the corresponding environment variable if the property is not set.
   *
   * @param properties the properties object containing the property
   * @param propertyName the property key
   * @return the property value or the default
   */
  public static String getPropertyOrNull(final Properties properties, final String propertyName) {

    return getPropertyOrDefault(properties, propertyName, null);
  }

  /**
   * Reads a property and returns a default if it doesn't exist or the value is a placeholder. Falls
   * back to the corresponding environment variable by replacing dots with underscores, removing
   * hyphens, and uppercasing (e.g. {@code judge.chatModel.apiKey} maps to {@code
   * JUDGE_CHATMODEL_APIKEY}).
   *
   * @param properties the properties object containing the property
   * @param propertyName the property key
   * @param defaultValue the value if the property doesn't exist or is a placeholder string
   * @return the property value or the default
   */
  public static String getPropertyOrDefault(
      final Properties properties, final String propertyName, final String defaultValue) {

    final String propertyValue = properties.getProperty(propertyName);
    if (propertyValue == null || isPlaceholder(propertyValue)) {
      final String envValue = System.getenv(toEnvVarName(propertyName));
      if (envValue != null && !envValue.isEmpty()) {
        return envValue;
      }
      return defaultValue;

    } else {
      return propertyValue;
    }
  }

  /**
   * Reads and transforms a property to a specific type. Returns null if it doesn't exist, the value
   * is a placeholder or the conversion failed.
   *
   * @param properties the properties object containing the property
   * @param propertyName the property key
   * @param converter a mapping function converting the value from a String to another type
   * @return the property value or the default
   */
  public static <T> T getPropertyOrNull(
      final Properties properties, final String propertyName, final Function<String, T> converter) {

    return getPropertyOrDefault(properties, propertyName, converter, null);
  }

  /**
   * Reads and transforms a property to a specific type. Returns a default value if it doesn't exist
   * or the value is a placeholder. This method will propagate any exceptions thrown by the
   * converter to the caller.
   *
   * @param properties the properties object containing the property
   * @param propertyName the property key
   * @param converter a mapping function converting the value from a String to another type
   * @param defaultValue if the property doesn't exist.
   * @return the property value or the default
   */
  public static <T> T getPropertyOrDefault(
      final Properties properties,
      final String propertyName,
      final Function<String, T> converter,
      final T defaultValue) {

    final String propertyValue = properties.getProperty(propertyName);
    if (propertyValue == null || isPlaceholder(propertyValue)) {
      final String envValue = System.getenv(toEnvVarName(propertyName));
      if (envValue != null && !envValue.isEmpty()) {
        return converter.apply(envValue);
      }
      return defaultValue;
    } else {
      return converter.apply(propertyValue);
    }
  }

  private static boolean isPlaceholder(final String propertyValue) {
    return PLACEHOLDER_PATTERN.matcher(propertyValue).matches();
  }

  /**
   * Converts a property name to an environment variable name by replacing dots with underscores,
   * removing hyphens, and uppercasing everything.
   *
   * <p>Example: {@code judge.chatModel.apiKey} becomes {@code JUDGE_CHATMODEL_APIKEY}.
   */
  static String toEnvVarName(final String propertyName) {
    return propertyName.toUpperCase().replace('.', '_').replace("-", "");
  }

  /**
   * Reads a map of properties based on a common prefix in the format
   *
   * <pre>
   * key.a=valueA
   * key.b=valueB
   * key.c=valueC
   * ...
   * </pre>
   *
   * <p>Falls back to environment variables following Spring relaxed binding conventions.
   * Placeholder values ({@code ${...}}) are resolved to the env var derived from the full property
   * name. Additionally, env vars matching the prefix are discovered as new entries (e.g. {@code
   * JUDGE_CHATMODEL_CUSTOMPROPERTIES_ENDPOINT} maps to key {@code endpoint}).
   *
   * @param properties the properties object to parse.
   * @param propertyNamePrefix the property key prefix, e.g. "key", not "key." nor "key.a"
   * @return the parsed map, or empty if no properties with the given prefix were found.
   */
  public static Map<String, String> getPropertyMapOrEmpty(
      final Properties properties, final String propertyNamePrefix) {

    return getPropertyMapOrEmpty(properties, propertyNamePrefix, Function.identity());
  }

  /**
   * Reads a map of properties based on a common prefix in the format
   *
   * <pre>
   * key.a=valueA
   * key.b=valueB
   * key.c=valueC
   * ...
   * </pre>
   *
   * <p>Falls back to environment variables following Spring relaxed binding conventions.
   *
   * @param properties the properties object to parse.
   * @param propertyNamePrefix the property key prefix, e.g. "key", not "key." nor "key.a"
   * @param converter function that converts the value to the expected type T.
   * @return the parsed map, or empty if no properties with the given prefix were found.
   */
  public static <T> Map<String, T> getPropertyMapOrEmpty(
      final Properties properties,
      final String propertyNamePrefix,
      final Function<String, T> converter) {

    return getPropertyMapOrEmpty(properties, propertyNamePrefix, converter, System.getenv());
  }

  /**
   * Package-private overload that accepts an explicit env var source for testability.
   *
   * <p>Resolution order per entry:
   *
   * <ol>
   *   <li>Property value from the properties file (if not a placeholder)
   *   <li>Env var derived from the full property name (if property is a placeholder or missing)
   *   <li>Env var discovery: scan env vars matching the prefix for entirely new entries
   * </ol>
   *
   * Properties-based entries take precedence over env-var-discovered entries.
   */
  static <T> Map<String, T> getPropertyMapOrEmpty(
      final Properties properties,
      final String propertyNamePrefix,
      final Function<String, T> converter,
      final Map<String, String> envVars) {

    final String validatedPrefix =
        propertyNamePrefix.trim().endsWith(".")
            ? propertyNamePrefix.trim()
            : propertyNamePrefix.trim() + ".";

    final Map<String, T> result = new HashMap<>();

    // 1. Collect from properties file, with placeholder → env var fallback
    for (final String key : properties.stringPropertyNames()) {
      if (key.startsWith(validatedPrefix)) {
        final String mapKey = key.substring(validatedPrefix.length());
        final String rawValue = properties.getProperty(key).trim();
        if (isPlaceholder(rawValue)) {
          final String envValue = envVars.get(toEnvVarName(key));
          if (envValue != null && !envValue.isEmpty()) {
            result.put(mapKey, converter.apply(envValue));
          }
        } else {
          result.put(mapKey, converter.apply(rawValue));
        }
      }
    }

    // 2. Discover additional entries from environment variables
    final String envPrefix = toEnvVarName(validatedPrefix);
    for (final Map.Entry<String, String> entry : envVars.entrySet()) {
      if (entry.getKey().startsWith(envPrefix)) {
        final String suffix = entry.getKey().substring(envPrefix.length());
        if (!suffix.isEmpty() && entry.getValue() != null && !entry.getValue().isEmpty()) {
          final String mapKey = suffix.toLowerCase();
          if (!result.containsKey(mapKey)) {
            result.put(mapKey, converter.apply(entry.getValue()));
          }
        }
      }
    }

    return result;
  }

  /**
   * Reads a list of properties based on a common prefix in the format
   *
   * <pre>
   * key[0]=valueA
   * key[1]=valueB
   * key[2]=valueC
   * ...
   * </pre>
   *
   * @param properties the properties object to parse.
   * @param propertyNamePrefix the property key without the array syntax, e.g. "key", not "key[0]"
   * @return the parsed list, or empty if no properties with the given prefix were found.
   */
  public static List<String> getPropertyListOrEmpty(
      final Properties properties, final String propertyNamePrefix) {

    return getPropertyListOrEmpty(properties, propertyNamePrefix, Function.identity());
  }

  /**
   * Reads a list of properties based on a common prefix in the format
   *
   * <pre>
   * key[0]=valueA
   * key[1]=valueB
   * key[2]=valueC
   * ...
   * </pre>
   *
   * @param properties the properties object to parse.
   * @param propertyNamePrefix the property key without the array syntax, e.g. "key", not "key[0]"
   * @param converter function that converts the value to the expected type T.
   * @return the parsed list, or empty if no properties with the given prefix were found.
   */
  public static <T> List<T> getPropertyListOrEmpty(
      final Properties properties,
      final String propertyNamePrefix,
      final Function<String, T> converter) {

    final Pattern propertyNamePattern =
        Pattern.compile(String.format("%s\\[\\d+]", propertyNamePrefix));

    return properties.stringPropertyNames().stream()
        .filter(key -> propertyNamePattern.matcher(key).matches())
        .map(key -> readProperty(properties, key, converter))
        .collect(Collectors.toList());
  }

  private static String readProperty(final Properties properties, final String key) {
    return readProperty(properties, key, Function.identity());
  }

  private static <T> T readProperty(
      final Properties properties, final String key, final Function<String, T> converter) {

    return converter.apply(properties.getProperty(key).trim());
  }
}
