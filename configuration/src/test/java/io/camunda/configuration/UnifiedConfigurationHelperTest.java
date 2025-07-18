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

  private static final String PROPERTIES_MUST_BE_REMOVED_SIGNATURE =
      "The following legacy properties must be removed";
  private static final String LEGACY_PROPERTIES_SAME_VALUE_SIGNATURE =
      "The legacy configuration properties must have the same value";
  private static final String LEGACY_NOT_ALLOWED_SIGNATURE =
      "The following legacy properties are no longer supported";

  Environment mockEnvironment;

  static final Map<String, Set<String>> SINGLE_LEGACY_PROPERTY =
      Map.of("modern.prop", Set.of("legacy.prop1"));

  static final Map<String, Set<String>> MULTIPLE_LEGACY_PROPERTIES =
      Map.of("modern.prop", Set.of("legacy.prop1", "legacy.prop2"));

  @BeforeEach
  void setup() {
    mockEnvironment = mock(Environment.class);
    UnifiedConfigurationHelper.setCustomEnvironment(mockEnvironment);
  }

  private void setLegacyPropertyValues(String key, String value) {
    Mockito.when(mockEnvironment.getProperty(key)).thenReturn(value);
  }

  // single legacy property and new property coexist with same value -> ok + warning
  @Test
  void test_1_newDefined_legacySingle_legacyMatchAllowed() {
    UnifiedConfigurationHelper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);

    // given
    final String newProperty = "modern.prop";
    final String newValue = "matchingValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    setLegacyPropertyValues("legacy.prop1", "matchingValue");

    String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);

    // then
    assertEquals(result, "matchingValue");
  }

  @Test
  void testFallbackErrorWhenSingleLegacyPropertyandNewPropertyCoexistWithDifferentValues() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = "newValue";
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);
    setLegacyPropertyValues("legacy.prop1", "legacyValue1");

    // then
    RuntimeException expectedException =
        assertThrows(
            RuntimeException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains(PROPERTIES_MUST_BE_REMOVED_SIGNATURE));
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
    setLegacyPropertyValues("legacy.prop1", "legacyValue1");
    setLegacyPropertyValues("legacy.prop2", "legacyValue2");

    // then
    RuntimeException expectedException =
        assertThrows(
            RuntimeException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains(LEGACY_PROPERTIES_SAME_VALUE_SIGNATURE));
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
    setLegacyPropertyValues("legacy.prop1", "legacyValue");
    setLegacyPropertyValues("legacy.prop2", "legacyValue");

    // then
    RuntimeException expectedException =
        assertThrows(
            RuntimeException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains(PROPERTIES_MUST_BE_REMOVED_SIGNATURE));
  }

  @Test
  void testFallbackSupportedWhenLegacyPropertyOnlyIsDefined() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = null;
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);
    setLegacyPropertyValues("legacy.prop1", "legacyValue1");

    // then
    String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);
    assertEquals(result, "legacyValue1");
  }

  @Test
  void testFallbackSupportedWhenMultipleLegacyPropertiesAreDefinedWithTheSameValue() {
    // given
    final String newProperty = "modern.prop";
    final String newValue = null;
    final boolean legacyConfigAllowed = true;
    final boolean onlyIfValuesMatch = true;

    // when
    UnifiedConfigurationHelper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setLegacyPropertyValues("legacy.prop1", "legacyValue");
    setLegacyPropertyValues("legacy.prop2", "legacyValue");

    // then
    String result =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch);
    assertEquals(result, "legacyValue");
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
    setLegacyPropertyValues("legacy.prop1", sameValue);
    setLegacyPropertyValues("legacy.prop2", sameValue);

    // then
    String result =
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
    setLegacyPropertyValues("legacy.prop1", "legacyValue");
    setLegacyPropertyValues("legacy.prop2", "legacyValue");

    // then
    RuntimeException expectedException =
        assertThrows(
            RuntimeException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains(PROPERTIES_MUST_BE_REMOVED_SIGNATURE));
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
    setLegacyPropertyValues("legacy.prop1", null);
    setLegacyPropertyValues("legacy.prop2", null);

    // then
    String result =
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
    IllegalArgumentException expectedException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertEquals(expectedException.getMessage(), UnifiedConfigurationHelper.WRONG_INPUT_ERROR);
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
    setLegacyPropertyValues("legacy.prop1", "legacyValue");
    setLegacyPropertyValues("legacy.prop2", "legacyValue");

    // then
    RuntimeException expectedException =
        assertThrows(
            RuntimeException.class,
            () ->
                UnifiedConfigurationHelper.validateLegacyConfiguration(
                    newProperty, newValue, String.class, legacyConfigAllowed, onlyIfValuesMatch));
    assertTrue(expectedException.getMessage().contains(LEGACY_NOT_ALLOWED_SIGNATURE));
  }
}
