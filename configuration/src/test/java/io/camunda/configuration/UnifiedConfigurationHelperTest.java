/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode.NOT_SUPPORTED;
import static io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED;
import static io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

class UnifiedConfigurationHelperTest {

  private static final String NEW_PROPERTY = "modern.prop";
  private static final Set<String> SINGLE_LEGACY_PROPERTY = Set.of("legacy.prop1");
  private static final Set<String> MULTIPLE_LEGACY_PROPERTIES =
      Set.of("legacy.prop1", "legacy.prop2");

  Environment mockEnvironment;

  @BeforeEach
  void setup() {
    mockEnvironment = mock(Environment.class);
    UnifiedConfigurationHelper.setCustomEnvironment(mockEnvironment);
  }

  private void setPropertyValues(String key, String value) {
    Mockito.when(mockEnvironment.getProperty(key)).thenReturn(value);
    Mockito.when(mockEnvironment.containsProperty(key)).thenReturn(true);
  }

  // single legacy property and new property coexist with same value -> ok + warning
  @Test
  void testNewDefinedLegacySingleLLegacyMatchAllowed() {
    // given
    final String newValue = "matchingValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(NEW_PROPERTY, newValue);
    setPropertyValues("legacy.prop1", "matchingValue");

    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            NEW_PROPERTY, newValue, String.class, mode, SINGLE_LEGACY_PROPERTY);

    // then
    assertThat(result).isEqualTo("matchingValue");
  }

  @Test
  void testFallbackErrorWhenMultipleLegacyPropertiesHaveDifferentValues() {
    // given

    final String newValue = "newValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(NEW_PROPERTY, newValue);
    setPropertyValues("legacy.prop1", "legacyValue1");
    setPropertyValues("legacy.prop2", "legacyValue2");

    // then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    NEW_PROPERTY, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES))
        .withMessageContaining("Ambiguous legacy configuration");
  }

  @Test
  void testFallbackErrorWhenMultipleLegacyPropertiesDifferentThanNewProperty() {
    // given
    final String newValue = "newValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(NEW_PROPERTY, newValue);
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    NEW_PROPERTY, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES))
        .withMessageContaining("Ambiguous configuration");
  }

  @Test
  void testFallbackSupportedWhenLegacyPropertyOnlyIsDefined() {
    // given
    final String newValue = null;
    final BackwardsCompatibilityMode mode = SUPPORTED;

    // when
    setPropertyValues("legacy.prop1", "legacyValue1");

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            NEW_PROPERTY, newValue, String.class, mode, SINGLE_LEGACY_PROPERTY);
    assertThat(result).isEqualTo("legacyValue1");
  }

  @Test
  void testFallbackSupportedWhenMultipleLegacyPropertiesAreDefinedWithTheSameValue() {
    // given
    final String newValue = "defaultValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(NEW_PROPERTY, newValue);
    setPropertyValues("legacy.prop1", "defaultValue");
    setPropertyValues("legacy.prop2", "defaultValue");

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            NEW_PROPERTY, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES);
    assertThat(result).isEqualTo("defaultValue");
  }

  @Test
  void testFallbackLeniencyWhenMultipleLegacyPropertiesHaveTheSameValueAsNewProperty() {
    // given
    final String sameValue = "sameValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(NEW_PROPERTY, sameValue);
    setPropertyValues("legacy.prop1", sameValue);
    setPropertyValues("legacy.prop2", sameValue);

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            NEW_PROPERTY, sameValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES);
    assertThat(result).isEqualTo(sameValue);
  }

  @Test
  void testFallbackErrorWhenLegacyValueAndNewValuesAreDifferent() {
    // given
    final String newValue = "newValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(NEW_PROPERTY, newValue);
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    NEW_PROPERTY, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES))
        .withMessageContaining("Ambiguous configuration");
  }

  @Test
  void testFallbackNoOpWhenLegacyPropertiesAreNotDefined() {
    // given
    final String newValue = "newValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(NEW_PROPERTY, newValue);

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            NEW_PROPERTY, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES);
    assertThat(result).isEqualTo(newValue);
  }

  @Test
  void testFallbackWrongFlagsConfiguration() {
    // given
    final String newProperty = "doesnt.matter";
    final String newValue = "doesntMatter";
    final BackwardsCompatibilityMode mode = null;

    // then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, mode, SINGLE_LEGACY_PROPERTY))
        .withMessageContaining("cannot be null");
  }

  @Test
  void testFallbackErrorWhenLegacyConfigIsNotSupported() {
    // given
    final String newValue = "doesntMatter";
    final BackwardsCompatibilityMode mode = NOT_SUPPORTED;

    // when
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    NEW_PROPERTY, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES))
        .withMessageContaining(
            "The following legacy configuration properties are no longer supported")
        .withMessageContaining("in favor of");
  }

  @Test
  void testNoPropertiesAreSetThenDefaultIsExpected() {
    // given
    final String defaultValue = "defaultValue";
    final BackwardsCompatibilityMode mode = SUPPORTED;

    final String expected =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            NEW_PROPERTY, defaultValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES);
    assertThat(expected).isEqualTo(defaultValue);
  }

  @Test
  void testLegacySetAndNewNotPresentThenLegacyIsExpected() {
    // given
    final String defaultValue = "defaultValue";
    final BackwardsCompatibilityMode mode = SUPPORTED;

    // when
    setPropertyValues("legacy.prop1", "legacyValue");

    final String expected = "legacyValue";
    final String actual =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            NEW_PROPERTY, defaultValue, String.class, mode, SINGLE_LEGACY_PROPERTY);
    assertThat(actual).isEqualTo(expected);
  }
}
