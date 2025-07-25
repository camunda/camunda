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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

class UnifiedConfigurationHelperTest {

  static final Set<String> SINGLE_LEGACY_PROPERTY = Set.of("legacy.prop1");
  static final Set<String> MULTIPLE_LEGACY_PROPERTIES = Set.of("legacy.prop1", "legacy.prop2");

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
    final String newProperty = "modern.prop";
    final String newValue = "matchingValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "matchingValue");

    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, mode, SINGLE_LEGACY_PROPERTY);

    // then
    assertEquals(result, "matchingValue");
  }

  @Test
  void testFallbackErrorWhenMultipleLegacyPropertiesHaveDifferentValues() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "legacyValue1");
    setPropertyValues("legacy.prop2", "legacyValue2");

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES));
    assertTrue(expectedException.getMessage().contains("Ambiguous legacy configuration"));
  }

  @Test
  void testFallbackErrorWhenMultipleLegacyPropertiesDifferentThanNewProperty() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES));
    assertTrue(expectedException.getMessage().contains("Ambiguous configuration"));
  }

  @Test
  void testFallbackSupportedWhenLegacyPropertyOnlyIsDefined() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = null;
    final BackwardsCompatibilityMode mode = SUPPORTED;

    // when
    setPropertyValues("legacy.prop1", "legacyValue1");

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, mode, SINGLE_LEGACY_PROPERTY);
    assertEquals(result, "legacyValue1");
  }

  @Test
  void testFallbackSupportedWhenMultipleLegacyPropertiesAreDefinedWithTheSameValue() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "defaultValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "defaultValue");
    setPropertyValues("legacy.prop2", "defaultValue");

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES);
    assertEquals(result, "defaultValue");
  }

  @Test
  void testFallbackLeniencyWhenMultipleLegacyPropertiesHaveTheSameValueAsNewProperty() {
    // given
    final String newProperty = "modern.prop";
    final String sameValue = "sameValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(newProperty, sameValue);
    setPropertyValues("legacy.prop1", sameValue);
    setPropertyValues("legacy.prop2", sameValue);

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, sameValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES);
    assertEquals(result, sameValue);
  }

  @Test
  void testFallbackErrorWhenLegacyValueAndNewValuesAreDifferent() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES));
    assertTrue(expectedException.getMessage().contains("Ambiguous configuration"));
  }

  @Test
  void testFallbackNoOpWhenLegacyPropertiesAreNotDefined() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final BackwardsCompatibilityMode mode = SUPPORTED_ONLY_IF_VALUES_MATCH;

    // when
    setPropertyValues(newProperty, newValue);

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES);
    assertEquals(result, newValue);
  }

  @Test
  void testFallbackWrongFlagsConfiguration() {
    // given
    final String newProperty = "doesnt.matter";
    final String newValue = "doesntMatter";
    final BackwardsCompatibilityMode mode = null;

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, mode, SINGLE_LEGACY_PROPERTY));
    assertTrue(expectedException.getMessage().contains("cannot be null"));
  }

  @Test
  void testFallbackErrorWhenLegacyConfigIsNotSupported() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "doesntMatter";
    final BackwardsCompatibilityMode mode = NOT_SUPPORTED;

    // when
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES));

    assertTrue(
        expectedException
            .getMessage()
            .contains("The following legacy configuration properties are no longer supported"));
    assertTrue(expectedException.getMessage().contains("in favor of"));
  }

  @Test
  void testNoPropertiesAreSetThenDefaultIsExpected() {
    // given
    final String newProperty = "modern.prop";
    final String defaultValue = "defaultValue";
    final BackwardsCompatibilityMode mode = SUPPORTED;

    final String expected =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, defaultValue, String.class, mode, MULTIPLE_LEGACY_PROPERTIES);
    assertEquals(expected, defaultValue);
  }

  @Test
  void testLegacySetAndNewNotPresentThenLegacyIsExpected() {
    // given
    final String newProperty = "modern.prop";
    final String defaultValue = "defaultValue";
    final BackwardsCompatibilityMode mode = SUPPORTED;

    // when
    setPropertyValues("legacy.prop1", "legacyValue");

    final String expected = "legacyValue";
    final String actual =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, defaultValue, String.class, mode, SINGLE_LEGACY_PROPERTY);
    assertEquals(expected, actual);
  }
}
