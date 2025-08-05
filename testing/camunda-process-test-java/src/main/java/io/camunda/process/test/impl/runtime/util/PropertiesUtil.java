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
   * Reads a property and returns a default if it doesn't exist or the value is a placeholder.
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
      return defaultValue;

    } else {
      return propertyValue;
    }
  }

  public static <T> T getPropertyOrDefault(
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

  private static boolean isPlaceholder(final String propertyValue) {
    return PLACEHOLDER_PATTERN.matcher(propertyValue).matches();
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
   * @param properties the properties object to parse.
   * @param propertyNamePrefix the property key prefix, e.g. "key", not "key." nor "key.a"
   * @param converter function that converts the value to the expected type T.
   * @return the parsed map, or empty if no properties with the given prefix were found.
   */
  public static <T> Map<String, T> getPropertyMapOrEmpty(
      final Properties properties,
      final String propertyNamePrefix,
      final Function<String, T> converter) {

    final String validatedPropertyNamePrefix =
        propertyNamePrefix.trim().endsWith(".")
            ? propertyNamePrefix.trim()
            : propertyNamePrefix.trim() + ".";

    return properties.stringPropertyNames().stream()
        .filter(key -> key.startsWith(validatedPropertyNamePrefix))
        .collect(
            Collectors.toMap(
                key -> key.substring(validatedPropertyNamePrefix.length()),
                key -> readProperty(properties, key, converter)));
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
