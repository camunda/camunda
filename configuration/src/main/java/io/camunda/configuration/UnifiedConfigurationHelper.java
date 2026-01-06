/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.exporter.config.ExporterConfiguration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component("unifiedConfigurationHelper")
public class UnifiedConfigurationHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedConfigurationHelper.class);
  private static final ConversionService CONVERSION_SERVICE = new ApplicationConversionService();

  private static Environment environment;
  private static ConfigurableEnvironment configurableEnvironment;

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
          backwardsCompatibilityNotSupported(legacyProperties, newProperty, newValue, expectedType);
      case SUPPORTED_ONLY_IF_VALUES_MATCH -> {
        final T legacyValue = getLegacyValue(legacyProperties, expectedType);
        yield backwardsCompatibilitySupportedOnlyIfValuesMatch(
            legacyProperties, legacyValue, newProperty, newValue, expectedType);
      }
      case SUPPORTED -> {
        final T legacyValue = getLegacyValue(legacyProperties, expectedType);
        yield backwardsCompatibilitySupported(
            legacyProperties, legacyValue, newProperty, newValue, expectedType);
      }
    };
  }

  private static <T> T getLegacyValue(
      final Set<String> legacyProperties, final ResolvableType expectedType) {
    final var legacyConfigurationValues = new HashMap<String, T>();

    for (final String legacyProperty : legacyProperties) {
      final T legacyValue = parseLegacyValue(legacyProperty, expectedType);

      LOGGER.trace("Parsing legacy property '{}' -> '{}'", legacyProperty, legacyValue);
      if (legacyValue != null) {
        legacyConfigurationValues.put(legacyProperty, legacyValue);
        LOGGER.trace("Parsed actual value: '{}'", legacyValue);
      } else {
        LOGGER.trace("Parsed null object");
      }
    }

    final Set<T> legacyValues = new HashSet<>(legacyConfigurationValues.values());
    if (legacyValues.isEmpty()) {
      return null;
    }

    if (legacyValues.size() > 1) {
      throw new UnifiedConfigurationException(
          String.format(
              "Ambiguous legacy configuration. Legacy properties: %s", legacyConfigurationValues));
    }

    return legacyValues.iterator().next();
  }

  private static <T> T backwardsCompatibilityNotSupported(
      final Set<String> legacyProperties,
      final String newProperty,
      final T newValue,
      final ResolvableType expectedType) {
    final boolean legacyPresent = legacyConfigPresent(legacyProperties, expectedType);
    final boolean newPresent = newConfigPresent(newProperty, expectedType);

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
      final T newValue,
      final ResolvableType expectedType) {
    final boolean legacyPresent = legacyConfigPresent(legacyProperties, expectedType);

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
      final T newValue,
      final ResolvableType expectedType) {
    final boolean legacyPresent = legacyConfigPresent(legacyProperties, expectedType);
    final boolean newPresent = newConfigPresent(newProperty, expectedType);

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

  private static boolean legacyConfigPresent(
      final Set<String> legacyProperties, final ResolvableType expectedType) {

    for (final String legacyProperty : legacyProperties) {
      if (environmentContainsProperty(legacyProperty, expectedType)) {
        LOGGER.trace("Found legacy property '{}'", legacyProperty);
        return true;
      }
    }

    return false;
  }

  private static boolean newConfigPresent(
      final String newProperty, final ResolvableType expectedType) {
    return environmentContainsProperty(newProperty, expectedType);
  }

  @SuppressWarnings("unchecked")
  private static <T> T parseLegacyValue(
      final String legacyProperty, final ResolvableType expectedType) {

    final Class<?> rawClass = expectedType.resolve();
    final ResolvableType[] generics = expectedType.getGenerics();

    // simple types
    if (generics.length == 0) {
      final String strValue = environment.getProperty(legacyProperty);
      if (strValue != null) {
        return (T) CONVERSION_SERVICE.convert(strValue, rawClass);
      }
      return null;
    }

    // generic types
    if (isPropertyCollection(expectedType) && generics.length == 1) {
      final Collection collection = getCollectionFromEnvironment(legacyProperty);
      if (collection != null) {
        final TypeDescriptor targetType =
            TypeDescriptor.collection(rawClass, TypeDescriptor.valueOf(generics[0].resolve()));
        return (T)
            CONVERSION_SERVICE.convert(
                collection, TypeDescriptor.valueOf(Collection.class), targetType);
      } else {
        return null;
      }
    }

    throw new IllegalArgumentException("Unsupported type: " + expectedType);
  }

  private static boolean environmentContainsProperty(
      final String property, final ResolvableType expectedType) {
    if (isPropertyCollection(expectedType)) {
      return !getCollectionFromEnvironment(property).isEmpty();
    } else {
      return environment.containsProperty(property);
    }
  }

  private static boolean isPropertyCollection(final ResolvableType expectedType) {
    return expectedType.resolve() != null
        && Collection.class.isAssignableFrom(expectedType.resolve());
  }

  private static Collection getCollectionFromEnvironment(final String property) {
    final ConfigurationPropertyName normalizedProperty =
        ConfigurationPropertyName.adapt(property, '.');
    return Binder.get(environment)
        .bind(normalizedProperty.toString(), Collection.class)
        .orElse(Collections.emptyList());
  }

  /* Helper methods */

  public static ExporterConfiguration argsToCamundaExporterConfiguration(
      final Map<String, Object> args) {
    return new io.camunda.zeebe.broker.exporter.context.ExporterConfiguration(
            "camundaExporter", args)
        .instantiate(ExporterConfiguration.class);
  }

  public static io.camunda.exporter.rdbms.ExporterConfiguration argsToRdbmsExporterConfiguration(
      final Map<String, Object> args) {
    return new io.camunda.zeebe.broker.exporter.context.ExporterConfiguration("rdbms", args)
        .instantiate(io.camunda.exporter.rdbms.ExporterConfiguration.class);
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
