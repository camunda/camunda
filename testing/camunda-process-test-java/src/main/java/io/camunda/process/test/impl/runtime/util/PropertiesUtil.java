/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime.util;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Convenience methods for extracting data from a Properties file. May include singular values,
 * map and lists.
 */
public class PropertiesUtil {
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{.*}");

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

  public static boolean isPlaceholder(final String propertyValue) {
    return PLACEHOLDER_PATTERN.matcher(propertyValue).matches();
  }

  public static Map<String, String> getPropertyMapOrEmpty(
      final Properties properties, final String propertyNamePrefix) {

    return properties.stringPropertyNames().stream()
        .filter(key -> key.startsWith(propertyNamePrefix))
        .collect(
            Collectors.toMap(
                key -> key.substring(propertyNamePrefix.length()),
                key -> readProperty(properties, key).trim()));
  }

  public static <T> List<T> getPropertyListOrEmpty(
      final Properties properties,
      final String propertyNamePrefix,
      final Function<String, T> converter) {

    return properties.stringPropertyNames().stream()
        .filter(key -> key.startsWith(propertyNamePrefix))
        .map(key -> readProperty(properties, key).trim())
        .map(converter)
        .collect(Collectors.toList());
  }

  public static String readProperty(final Properties properties, final String key) {
    return properties.getProperty(key);
  }
}
