/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

@ComponentScan("io.camunda.configuration")
public class UnifiedConfigurationHelper {

  private static UnifiedConfigurationHelper instance;

  private static final Map<String, Set<String>> LEGACY_PROPERTIES =
      Map.of(
          "camunda.data.secondary-storage.elasticsearch.url",
          Set.of(
              "camunda.database.url",
              "camunda.operate.elasticsearch.url",
              "camunda.operate.zeebeElasticsearch.url",
              "camunda.tasklist.elasticsearch.url",
              "camunda.tasklist.zeebeElasticsearch.url"));

  private static final String LEGACY_PROPERTIES_DIFFERENT_VALUES_ERROR =
      "Invalid configuration. "
          + "The legacy configuration properties must have the same value. \n"
          + "Legacy properties: %s\n"
          + "Values: %s";

  private static final String LEGACY_MODERN_PROPERTIES_DIFFERENT_VALUES_ERROR =
      "Invalid configuration. "
          + "The following legacy properties must be removed in favor of '%s':\n"
          + "Legacy properties: %s";

  private static final String LEGACY_MODERN_PROPERTIES_DIFFERENT_VALUES_WARNING =
      "Invalid configuration. "
          + "The following legacy properties should be removed in favor of '%s':\n"
          + "Legacy properties: %s";

  private static final String LEGACY_PROPERTIES_NOT_ALLOWED_ERROR =
      "Invalid configuration. "
          + "The following legacy properties are no longer supported and must be removed:\n"
          + "Legacy properties: %s";

  public static final String WRONG_INPUT_ERROR =
      "onlyIfValuesMatch cannot be true without legacyConfigAllowed.";

  private static final String WRONG_MODERN_PROPERTY_ERROR =
      "Property %s does not need legacy configuration validation.";

  // --- Exposed static interface ---

  public static <T> T validateLegacyConfiguration(
      final String newProperty,
      final T newValue,
      final Class<T> expectedType,
      final boolean legacyConfigAllowed,
      final boolean onlyIfValuesMatch) {
    return instance.instanceValidateLegacyConfiguration(
        newProperty, newValue, expectedType, legacyConfigAllowed, onlyIfValuesMatch);
  }

  // --- Instance ---

  private Environment environment;
  private Map<String, Set<String>> legacyProperties;
  private final Logger LOGGER = LoggerFactory.getLogger(UnifiedConfigurationHelper.class);

  public UnifiedConfigurationHelper(@Autowired Environment environment) {
    this.environment = environment;
    this.legacyProperties = LEGACY_PROPERTIES;
    UnifiedConfigurationHelper.instance = this;
  }

  private <T> T instanceValidateLegacyConfiguration(
      final String newProperty,
      final T newValue,
      final Class<T> expectedType,
      final boolean legacyConfigAllowed,
      final boolean onlyIfValuesMatch) {

    if (onlyIfValuesMatch && !legacyConfigAllowed) {
      throw new IllegalArgumentException(WRONG_INPUT_ERROR);
    }

    final Set<String> legacyProperties = getLegacyProperties(newProperty);
    if (legacyProperties == null) {
      throw new IllegalArgumentException(String.format(WRONG_MODERN_PROPERTY_ERROR, newProperty));
    }

    Set<T> legacyValues = new HashSet<>();
    for (String legacyProperty : legacyProperties) {
      final String value = getEnvironment().getProperty(legacyProperty);
      if (isSet(value)) {
        legacyValues.add(parseLegacyValue(value, expectedType));
      }
    }

    // |legacyValues| = 0 covered
    if (legacyValues.isEmpty()) {
      return newValue;
    }

    if (!legacyConfigAllowed) {
      final String errorMessage =
          String.format(LEGACY_PROPERTIES_NOT_ALLOWED_ERROR, String.join(", ", legacyProperties));
      throw new RuntimeException(errorMessage);
    }

    // |legacyValues| can only be 1
    if (legacyValues.size() != 1) {
      String errorMessage =
          String.format(
              LEGACY_PROPERTIES_DIFFERENT_VALUES_ERROR,
              String.join(", ", legacyProperties),
              String.join(", ", legacyValues.stream().map(Object::toString).toList()));
      throw new RuntimeException(errorMessage);
    }

    T legacyValue = legacyValues.iterator().next();

    if (isSet(legacyValue) && isUnset(newValue)) {
      logDeprecationMessage(legacyProperties, newProperty);
      return legacyValue;
    }

    if (onlyIfValuesMatch && !Objects.equals(legacyValue, newValue)) {
      String errorMessage =
          String.format(
              LEGACY_MODERN_PROPERTIES_DIFFERENT_VALUES_ERROR,
              String.join(", ", legacyProperties),
              newProperty);
      throw new RuntimeException(errorMessage);
    }

    return newValue;
  }

  private void logDeprecationMessage(
      final Set<String> legacyProperties, final String newProperty) {
    // TODO-?: Maybe we could print this deprecation message only once, instead of printing it every
    //  time the property is accessed through its .get().

    String deprecationMessage =
        String.format(
            LEGACY_MODERN_PROPERTIES_DIFFERENT_VALUES_WARNING,
            String.join(", ", legacyProperties),
            newProperty);
    LOGGER.warn(deprecationMessage);
  }

  @SuppressWarnings("unchecked")
  private <T> T parseLegacyValue(final String strValue, final Class<T> type) {
    if (strValue == null) return null;

    return switch(type.getSimpleName()) {
      case "String" -> (T) strValue;
      case "Integer" -> (T) Integer.valueOf(strValue);
      case "Boolean" -> (T) Boolean.valueOf(strValue);
      case "Duration" -> (T) Duration.parse(strValue);
      default -> throw new IllegalArgumentException("Unsupported type: " + type);
    };
  }

  private boolean isUnset(Object value) {
    if (value == null) return true;
    if (value instanceof String) return ((String) value).trim().isEmpty();
    return false;
  }

  private boolean isSet(Object value) {
    return !isUnset(value);
  }

  public Set<String> getLegacyProperties(String newProperty) {
    return legacyProperties.get(newProperty);
  }

  public Environment getEnvironment() {
    return environment;
  }

  // Allows tests to inject the mock properties map.
  public void setCustomLegacyProperties(final Map<String, Set<String>> legacyProperties) {
    this.legacyProperties = legacyProperties;
  }
}
