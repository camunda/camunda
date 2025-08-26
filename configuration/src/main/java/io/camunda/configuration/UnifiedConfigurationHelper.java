/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component("unifiedConfigurationHelper")
public class UnifiedConfigurationHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedConfigurationHelper.class);
  private static final ConversionService CONVERSION_SERVICE = new ApplicationConversionService();

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

    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        newProperty,
        newValue,
        ResolvableType.forClass(expectedType),
        backwardsCompatibilityMode,
        legacyProperties);
  }

  public static <T> T validateLegacyConfiguration(
      final String newProperty,
      final T newValue,
      final ResolvableType expectedType,
      final BackwardsCompatibilityMode backwardsCompatibilityMode,
      final Set<String> legacyProperties) {

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
      final Set<String> legacyProperties, final ResolvableType expectedType) {
    final Set<T> legacyValues = new HashSet<>();

    for (final String legacyProperty : legacyProperties) {
      final String strValue = environment.getProperty(legacyProperty);
      final T legacyValue = parseLegacyValue(strValue, expectedType);

      LOGGER.debug("Parsing legacy property '" + legacyProperty + "' -> '" + legacyValue + "'");
      if (legacyValue != null) {
        legacyValues.add(legacyValue);
        LOGGER.debug("Parsed actual value: '" + legacyValue + "'");
      } else {
        LOGGER.debug("Parsed null object");
      }
    }

    if (legacyValues.isEmpty()) {
      return null;
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
              "Ambiguous configuration. The value %s=%s conflicts with the values '%s' from the legacy properties %s",
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
        LOGGER.debug("Found legacy property '{}'", legacyProperty);
        return true;
      }
    }

    return false;
  }

  private static boolean newConfigPresent(final String newProperty) {
    return environment.containsProperty(newProperty);
  }

  @SuppressWarnings("unchecked")
  private static <T> T parseLegacyValue(final String strValue, final ResolvableType expectedType) {
    if (strValue == null) {
      return null;
    }

    final Class<?> rawClass = expectedType.resolve();
    final ResolvableType[] generics = expectedType.getGenerics();

    // simple types
    if (generics.length == 0) {
      return (T) CONVERSION_SERVICE.convert(strValue, rawClass);
    }

    // generic types
    if (Collection.class.isAssignableFrom(rawClass) && generics.length == 1) {
      final TypeDescriptor targetType =
          TypeDescriptor.collection(rawClass, TypeDescriptor.valueOf(generics[0].resolve()));
      return (T)
          CONVERSION_SERVICE.convert(strValue, TypeDescriptor.valueOf(String.class), targetType);
    }

    throw new IllegalArgumentException("Unsupported type: " + expectedType);
  }

  /* Helper methods */

  public static ExporterCfg getCamundaExporter(final BrokerBasedProperties brokerBasedProperties) {
    final List<ExporterCfg> exporters =
        brokerBasedProperties.getExporters().values().stream()
            .filter(e -> e.getClassName().equals("io.camunda.exporter.CamundaExporter"))
            .toList();
    if (exporters.isEmpty()) {
      return null;
    }

    return exporters.get(0);
  }

  public static ExporterConfiguration argsToExporterConfiguration(final Map<String, Object> args) {
    return new io.camunda.zeebe.broker.exporter.context.ExporterConfiguration(
            "camundaExporter", args)
        .instantiate(ExporterConfiguration.class);
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
