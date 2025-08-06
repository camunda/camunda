/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.Gcs.GcsBackupStoreAuth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component("unifiedConfigurationHelper")
public class UnifiedConfigurationHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedConfigurationHelper.class);

  private static Environment environment;

  public UnifiedConfigurationHelper(@Autowired final Environment environment) {
    // We need to pin the environment object statically so that it can be used to perform the
    // fallback mechanism.
    UnifiedConfigurationHelper.environment = environment;
  }

  public static <T> T validateLegacyConfiguration(
      final String newProperty,
      final T newValue,
      final Class<T> expectedType,
      final BackwardsCompatibilityMode backwardsCompatibilityMode,
      final Set<String> legacyProperties) {
    // Input validation
    if (backwardsCompatibilityMode == null) {
      throw new UnifiedConfigurationException("backwardsCompatibilityMode cannot be null");
    }
    if (legacyProperties == null) {
      throw new UnifiedConfigurationException("legacyProperties cannot be null");
    }

    return switch (backwardsCompatibilityMode) {
      case NOT_SUPPORTED ->
          backwardsCompatibilityNotSupported(legacyProperties, newProperty, newValue);
      case SUPPORTED_ONLY_IF_VALUES_MATCH -> {
        final T legacyValue = getLegacyValue(legacyProperties, expectedType);
        yield backwardsCompatibilitySupportedOnlyIfValuesMatch(
            legacyProperties, legacyValue, newProperty, newValue);
      }
      case SUPPORTED -> {
        final T legacyValue = getLegacyValue(legacyProperties, expectedType);
        yield backwardsCompatibilitySupported(legacyProperties, legacyValue, newProperty, newValue);
      }
    };
  }

  private static <T> T getLegacyValue(
      final Set<String> legacyProperties, final Class<T> expectedType) {
    final Set<T> legacyValues = new HashSet<>();

    for (final String legacyProperty : legacyProperties) {
      final String strValue = environment.getProperty(legacyProperty);
      final T legacyValue = parseLegacyValue(strValue, expectedType);
      legacyValues.add(legacyValue);
    }

    if (legacyValues.size() > 1) {
      throw new UnifiedConfigurationException(
          String.format(
              "Ambiguous legacy configuration. Legacy properties: %s; Legacy values: %s",
              String.join(", ", legacyProperties), legacyValues));
    }

    return legacyValues.iterator().next();
  }

  private static <T> T backwardsCompatibilityNotSupported(
      final Set<String> legacyProperties, final String newProperty, final T newValue) {
    final boolean legacyPresent = legacyConfigPresent(legacyProperties);
    final boolean newPresent = newConfigPresent(newProperty);

    final String warningMessage =
        String.format(
            "The following legacy properties are no longer supported and should be removed in favor of '%s': %s",
            newProperty, String.join(", ", legacyProperties));

    if (!legacyPresent) {
      // Legacy config: not present
      // We can return the newValue or default value
      return newValue;
    } else if (!newPresent) {
      // Legacy config: present
      // New config...: not present
      // The legacy configuration is no longer allowed -> error
      final String errorMessage =
          String.format(
              "The following legacy configuration properties are no longer supported and must be removed in favor of '%s': %s",
              newProperty, String.join(", ", legacyProperties));
      throw new UnifiedConfigurationException(errorMessage);
    } else {
      // Legacy config: present
      // New config...: present
      // We can return the new value and log a warning
      LOGGER.warn(warningMessage);
      return newValue;
    }
  }

  private static <T> T backwardsCompatibilitySupportedOnlyIfValuesMatch(
      final Set<String> legacyProperties,
      final T legacyValue,
      final String newProperty,
      final T newValue) {
    final boolean legacyPresent = legacyConfigPresent(legacyProperties);

    final String warningMessage =
        String.format(
            "The following legacy properties are no longer supported and should be removed in favor of '%s': %s",
            newProperty, String.join(", ", legacyProperties));

    if (!legacyPresent) {
      // Legacy config: not present
      // New config...: not present
      // or
      // Legacy config: not present
      // New config...: present
      // then
      // We can return the new value, either default or declared
      return newValue;
    } else {
      // Legacy config: present
      // New config...: not present
      // or
      // Legacy config: present
      // New config...: present
      // then
      // We can return only if the new default value and the legacy value match
      if (Objects.equals(legacyValue, newValue)) {
        LOGGER.warn(warningMessage);
        return newValue;
      }

      final String errorMessage =
          String.format(
              "Ambiguous configuration. The value %s=%s does not match the value(s) conflicts with the values '%s' from the legacy properties %s",
              newProperty, newValue, legacyValue, String.join(", ", legacyProperties));
      throw new UnifiedConfigurationException(errorMessage);
    }
  }

  private static <T> T backwardsCompatibilitySupported(
      final Set<String> legacyProperties,
      final T legacyValue,
      final String newProperty,
      final T newValue) {
    final boolean legacyPresent = legacyConfigPresent(legacyProperties);
    final boolean newPresent = newConfigPresent(newProperty);

    final String warningMessage =
        String.format(
            "The following legacy configuration properties should be removed in favor of '%s': %s",
            newProperty, String.join(", ", legacyProperties));

    if (!legacyPresent) {
      // Legacy config: not present
      // New config...: not present
      // We can retrun the new default value
      return newValue;
    } else if (!newPresent) {
      // Legacy config: present
      // New config...: not present
      // We can return the legacy value
      LOGGER.warn(warningMessage);
      return legacyValue;
    } else {
      // Legacy config: present
      // New config...: present
      // The new value wins
      LOGGER.warn(warningMessage);
      return newValue;
    }
  }

  private static boolean legacyConfigPresent(final Set<String> legacyProperties) {
    for (final String legacyProperty : legacyProperties) {
      if (environment.containsProperty(legacyProperty)) {
        return true;
      }
    }

    return false;
  }

  private static boolean newConfigPresent(final String newProperty) {
    return environment.containsProperty(newProperty);
  }

  @SuppressWarnings("unchecked")
  private static <T> T parseLegacyValue(final String strValue, final Class<T> type) {
    if (strValue == null) {
      return null;
    }

    return switch (type.getSimpleName()) {
      case "String" -> (T) strValue;
      case "Integer" -> (T) Integer.valueOf(strValue);
      case "Boolean" -> (T) Boolean.valueOf(strValue);
      case "Duration" -> (T) DurationStyle.detectAndParse(strValue);
      case "Long" -> (T) Long.valueOf(strValue);
      case "List" -> (T) parseList(strValue);
      case "DataSize" -> (T) DataSize.parse(strValue);
      case "GcsBackupStoreAuth" -> (T) GcsBackupStoreAuth.valueOf(strValue.toUpperCase());
      default -> throw new IllegalArgumentException("Unsupported type: " + type);
    };
  }

  // FIXME: move to type ref
  private static <T> Object parseList(final String strValue) {
    if (strValue == null || strValue.trim().isEmpty()) {
      return new ArrayList<>();
    }

    // Simple comma-split (you might want more sophisticated parsing for quoted strings)
    return Arrays.stream(strValue.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  /* Setters used by tests to inject the mock objects */

  public static void setCustomEnvironment(final Environment environment) {
    UnifiedConfigurationHelper.environment = environment;
  }

  public enum BackwardsCompatibilityMode {
    NOT_SUPPORTED,
    SUPPORTED_ONLY_IF_VALUES_MATCH,
    SUPPORTED
  }
}
