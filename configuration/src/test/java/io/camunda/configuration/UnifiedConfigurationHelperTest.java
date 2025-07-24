/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

class UnifiedConfigurationHelperTest {

  static final Map<String, Set<String>> SINGLE_LEGACY_PROPERTY =
      Map.of("modern.prop", Set.of("legacy.prop1"));

  static final Map<String, Set<String>> MULTIPLE_LEGACY_PROPERTIES =
      Map.of("modern.prop", Set.of("legacy.prop1", "legacy.prop2"));

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
    UnifiedConfigurationHelper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);

    // given
    final String newProperty = "modern.prop";
    final String newValue = "matchingValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "matchingValue");

    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);

    // then
    assertEquals(result, "matchingValue");
  }

  @Test
  void testFallbackErrorWhenMultipleLegacyPropertiesHaveDifferentValues() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "legacyValue1");
    setPropertyValues("legacy.prop2", "legacyValue2");

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains("Ambiguous legacy configuration"));
  }

  @Test
  void testFallbackErrorWhenMultipleLegacyPropertiesDifferentThanNewProperty() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains("Ambiguous configuration"));
  }

  @Test
  void testFallbackSupportedWhenLegacyPropertyOnlyIsDefined() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = null;
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = false;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);
    setPropertyValues("legacy.prop1", "legacyValue1");

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);
    assertEquals(result, "legacyValue1");
  }

  @Test
  void testFallbackSupportedWhenMultipleLegacyPropertiesAreDefinedWithTheSameValue() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "defaultValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "defaultValue");
    setPropertyValues("legacy.prop2", "defaultValue");

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);
    assertEquals(result, "defaultValue");
  }

  @Test
  void testFallbackLeniencyWhenMultipleLegacyPropertiesHaveTheSameValueAsNewProperty() {
    // given
    final String newProperty = "modern.prop";
    final String sameValue = "sameValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setPropertyValues(newProperty, sameValue);
    setPropertyValues("legacy.prop1", sameValue);
    setPropertyValues("legacy.prop2", sameValue);

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, sameValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);
    assertEquals(result, sameValue);
  }

  @Test
  void testFallbackErrorWhenLegacyValueAndNewValuesAreDifferent() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setPropertyValues(newProperty, newValue);
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains("Ambiguous configuration"));
  }

  @Test
  void testFallbackNoOpWhenLegacyPropertiesAreNotDefined() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setPropertyValues(newProperty, newValue);

    // then
    final String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);
    assertEquals(result, newValue);
  }

  @Test
  void testFallbackWrongFlagsConfiguration() {
    // given
    final String newProperty = "doesnt.matter";
    final String newValue = "doesntMatter";
    final boolean legacyConfigAllowed = false;
    final boolean onlyIfValuesMatch = true;

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains("cannot be true"));
  }

  @Test
  void testFallbackErrorWhenLegacyConfigIsNotSupported() {
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    // given
    final String newProperty = "modern.prop";
    final String newValue = "doesntMatter";
    final boolean legacyConfigAllowed = false;
    final boolean onlyIfValuesMatch = false;

    // when
    setPropertyValues("legacy.prop1", "legacyValue");
    setPropertyValues("legacy.prop2", "legacyValue");

    // then
    final UnifiedConfigurationException expectedException =
        assertThrows(
            UnifiedConfigurationException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));

    assertTrue(
        expectedException
            .getMessage()
            .contains("The following legacy configuration properties are no longer supported"));
    assertTrue(expectedException.getMessage().contains("in favor of"));
  }

  @Test
  void testNoPropertiesAreSetThenDefaultIsExpected() {
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    // given
    final String newProperty = "modern.prop";
    final String defaultValue = "defaultValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = false;

    final String expected =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, defaultValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);
    assertEquals(expected, defaultValue);
  }

  @Test
  void testLegacySetAndNewNotPresentThenLegacyIsExpected() {
    UnifiedConfigurationHelper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);
    // given
    final String newProperty = "modern.prop";
    final String defaultValue = "defaultValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = false;

    // when
    setPropertyValues("legacy.prop1", "legacyValue");

    final String expected = "legacyValue";
    final String actual =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, defaultValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);
    assertEquals(expected, actual);
  }
}
