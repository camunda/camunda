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

  /**
   * Validates the legacy configuration properties based on the provided backward compatibility mode
   * and returns the appropriate value. This method could potentially log the value of the
   * properties (both unified configuration ones and legacy one), so it is not safe to use for
   * sensitive data. Use {@link #validateSensitiveLegacyConfiguration} instead in that case.
   *
   * @param newProperty the new unified configuration property name
   * @param newValue the new unified configuration property value
   * @param expectedType the expected type of the configuration property
   * @param backwardsCompatibilityMode the backward compatibility mode to apply when validating the
   *     legacy properties
   * @param legacyProperties the set of legacy configuration property names to validate
   * @return the value to use for the configuration property
   * @throws UnifiedConfigurationException if the legacy configuration is not valid according to the
   *     backward compatibility mode
   */
  public static <T> T validateLegacyConfigurationUnsafe(
      final String newProperty,
      final T newValue,
      final Class<T> expectedType,
      final BackwardsCompatibilityMode backwardsCompatibilityMode,
      final Set<String> legacyProperties) {
    return validateLegacyConfiguration(
        newProperty, newValue, expectedType, backwardsCompatibilityMode, legacyProperties, false);
  }

  /**
   * Validates the legacy configuration properties based on the provided backward compatibility mode
   * and returns the appropriate value. This method could potentially log the value of the
   * properties (both unified configuration ones and legacy one), so it is not safe to use for
   * sensitive data. Use {@link #validateSensitiveLegacyConfiguration} instead in that case.
   *
   * @param newProperty the new unified configuration property name
   * @param newValue the new unified configuration property value
   * @param expectedType the expected type of the configuration property
   * @param backwardsCompatibilityMode the backward compatibility mode to apply when validating the
   *     legacy properties
   * @param legacyProperties the set of legacy configuration property names to validate
   * @return the value to use for the configuration property
   * @throws UnifiedConfigurationException if the legacy configuration is not valid according to the
   *     backward compatibility mode
   */
  public static <T> T validateLegacyConfigurationUnsafe(
      final String newProperty,
      final T newValue,
      final ResolvableType expectedType,
      final BackwardsCompatibilityMode backwardsCompatibilityMode,
      final Set<String> legacyProperties) {
    return validateLegacyConfiguration(
        newProperty, newValue, expectedType, backwardsCompatibilityMode, legacyProperties, false);
  }

  /**
   * Validates the legacy configuration properties based on the provided backward compatibility mode
   * and returns the appropriate value. This method will not log the value of the properties, so it
   * is safe to use for sensitive data.
   *
   * @param newProperty the new unified configuration property name
   * @param newValue the new unified configuration property value
   * @param expectedType the expected type of the configuration property
   * @param backwardsCompatibilityMode the backward compatibility mode to apply when validating the
   *     legacy properties
   * @param legacyProperties the set of legacy configuration property names to validate
   * @return the value to use for the configuration property
   * @throws UnifiedConfigurationException if the legacy configuration is not valid according to the
   *     backward compatibility mode
   */
  public static <T> T validateSensitiveLegacyConfiguration(
      final String newProperty,
      final T newValue,
      final Class<T> expectedType,
      final BackwardsCompatibilityMode backwardsCompatibilityMode,
      final Set<String> legacyProperties) {
    return validateLegacyConfiguration(
        newProperty, newValue, expectedType, backwardsCompatibilityMode, legacyProperties, true);
  }

  /**
   * Validates the legacy configuration properties based on the provided backward compatibility mode
   * and returns the appropriate value. This method will not log the value of the properties, so it
   * is safe to use for sensitive data.
   *
   * @param newProperty the new unified configuration property name
   * @param newValue the new unified configuration property value
   * @param expectedType the expected type of the configuration property
   * @param backwardsCompatibilityMode the backward compatibility mode to apply when validating the
   *     legacy properties
   * @param legacyProperties the set of legacy configuration property names to validate
   * @return the value to use for the configuration property
   * @throws UnifiedConfigurationException if the legacy configuration is not valid according to the
   *     backward compatibility mode
   */
  public static <T> T validateSensitiveLegacyConfiguration(
      final String newProperty,
      final T newValue,
      final ResolvableType expectedType,
      final BackwardsCompatibilityMode backwardsCompatibilityMode,
      final Set<String> legacyProperties) {
    return validateLegacyConfiguration(
        newProperty, newValue, expectedType, backwardsCompatibilityMode, legacyProperties, true);
  }

  private static <T> T validateLegacyConfiguration(
      final String newProperty,
      final T newValue,
      final Class<T> expectedType,
      final BackwardsCompatibilityMode backwardsCompatibilityMode,
      final Set<String> legacyProperties,
      final boolean isSensitiveData) {

    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        newProperty,
        newValue,
        ResolvableType.forClass(expectedType),
        backwardsCompatibilityMode,
        legacyProperties,
        isSensitiveData);
  }

  private static <T> T validateLegacyConfiguration(
      final String newProperty,
      final T newValue,
      final ResolvableType expectedType,
      final BackwardsCompatibilityMode backwardsCompatibilityMode,
      final Set<String> legacyProperties,
      final boolean isSensitiveData) {

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
        final T legacyValue = getLegacyValue(legacyProperties, expectedType, isSensitiveData);
        yield backwardsCompatibilitySupportedOnlyIfValuesMatch(
            legacyProperties, legacyValue, newProperty, newValue, expectedType, isSensitiveData);
      }
      case SUPPORTED -> {
        final T legacyValue = getLegacyValue(legacyProperties, expectedType, isSensitiveData);
        yield backwardsCompatibilitySupported(
            legacyProperties, legacyValue, newProperty, newValue, expectedType);
      }
    };
  }

  private static <T> T getLegacyValue(
      final Set<String> legacyProperties,
      final ResolvableType expectedType,
      final boolean isSensitiveData) {
    final var legacyConfigurationDisplayValues = new HashMap<String, String>();
    final var legacyValues = new HashSet<T>();

    for (final String legacyProperty : legacyProperties) {
      final T legacyValue = parseLegacyValue(legacyProperty, expectedType);
      final String legacyDisplayValue = displayValue(legacyValue, isSensitiveData);

      LOGGER.trace("Parsing legacy property '{}' -> {}", legacyProperty, legacyDisplayValue);
      if (legacyValue != null) {
        legacyConfigurationDisplayValues.put(legacyProperty, legacyDisplayValue);
        legacyValues.add(legacyValue);
        LOGGER.trace("Parsed actual value: {}", legacyDisplayValue);
      } else {
        LOGGER.trace("Parsed null object");
      }
    }

    if (legacyValues.isEmpty()) {
      return null;
    }

    if (legacyValues.size() > 1) {
      throw new UnifiedConfigurationException(
          String.format(
              "Ambiguous legacy configuration. Legacy properties: %s",
              legacyConfigurationDisplayValues));
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
      final ResolvableType expectedType,
      final boolean isSensitiveData) {
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
              newProperty,
              displayValue(newValue, isSensitiveData),
              displayValue(legacyValue, isSensitiveData),
              String.join(", ", legacyProperties));
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

  private static <T> String displayValue(final T value, final boolean isSensitiveData) {
    return isSensitiveData ? "*****" : value != null ? value.toString() : "<null>";
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
