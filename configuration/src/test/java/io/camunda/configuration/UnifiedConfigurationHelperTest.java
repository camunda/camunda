package io.camunda.configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.Set;
import java.util.Map;
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

  UnifiedConfigurationHelper helper;
  Environment mockEnvironment;

  static final Map<String, Set<String>> SINGLE_LEGACY_PROPERTY = Map.of(
      "modern.prop",
      Set.of(
          "legacy.prop1")
  );

  static final Map<String, Set<String>> MULTIPLE_LEGACY_PROPERTIES = Map.of(
      "modern.prop",
      Set.of(
          "legacy.prop1",
          "legacy.prop2")
  );

  @BeforeEach
  void setup() {
    mockEnvironment = mock(Environment.class);
    helper = new UnifiedConfigurationHelper(mockEnvironment);
  }

  private void setLegacyPropertyValues(String key, String value) {
    Mockito.when(mockEnvironment.getProperty(key)).thenReturn(value);
  }

  /* ---------------------------------------------------------------------------------
     Test cases
     ---------------------------------------------------------------------------------
     - newValue { defined, undefined }                    || 2
     - legacyValue { single, multiple, undefined }        || 3
     - legacyAllowed x onlyIfMatch {
       true, true
       true, false
       false, false }                                     || 3
     ---------------------------------------------------------------------------------
   */

  // single legacy property and new property coexist with same value -> ok + warning
  @Test
  void test_1_newDefined_legacySingle_legacyMatchAllowed() {
    helper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);
    setLegacyPropertyValues("legacy.prop1", "matchingValue");
    String result = helper.validateLegacyConfiguration(
        "modern.prop", "matchingValue", String.class, true, true);
    assertEquals(result, "matchingValue");
  }

  // single legacy property and new property coexist but with different values -> unacceptable
  @Test
  void test_2_newDefined_legacySingle_legacyMismatchNotAllowed() {
    helper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);
    setLegacyPropertyValues("legacy.prop1", "legacyValue1");
    RuntimeException expectedException = assertThrows(RuntimeException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "newValue", String.class, true, true));
    assertTrue(expectedException.getMessage().contains(PROPERTIES_MUST_BE_REMOVED_SIGNATURE));
  }

  // multiple legacy properties with different values -> unacceptable
  @Test
  void test_3_newDefined_legacyMultipleButMismatching() {
    helper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setLegacyPropertyValues("legacy.prop1", "legacyValue1");
    setLegacyPropertyValues("legacy.prop2", "legacyValue2");
    RuntimeException expectedException = assertThrows(RuntimeException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "newValue", String.class, true, true));
    assertTrue(expectedException.getMessage().contains(LEGACY_PROPERTIES_SAME_VALUE_SIGNATURE));
  }

  // multiple legacy properties with the same value, new property with different value -> unacceptable
  @Test
  void test_4_newDefined_legacyMultiple_differentValues() {
    helper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setLegacyPropertyValues("legacy.prop1", "legacyValue");
    setLegacyPropertyValues("legacy.prop2", "legacyValue");
    RuntimeException expectedException = assertThrows(RuntimeException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "newValue", String.class, true, true));
    assertTrue(expectedException.getMessage().contains(PROPERTIES_MUST_BE_REMOVED_SIGNATURE));
  }

  // fallback: single legacy property -> legacyValue returned + warning
  @Test
  void test_5_fallbackToSingleLegacyProperty() {
    helper.setCustomLegacyProperties(SINGLE_LEGACY_PROPERTY);
    setLegacyPropertyValues("legacy.prop1", "legacyValue1");
    String result = helper.validateLegacyConfiguration(
        "modern.prop", null, String.class, true, true);
    assertEquals(result, "legacyValue1");
  }

  // fallback: multiple legacy properties with same value -> legacyValue returned + warning
  @Test
  void test_6_fallbackMultipleLegacyProperty() {
    helper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setLegacyPropertyValues("legacy.prop1", "legacyValue");
    setLegacyPropertyValues("legacy.prop2", "legacyValue");
    String result = helper.validateLegacyConfiguration(
        "modern.prop", null, String.class, true, true);
    assertEquals(result, "legacyValue");
  }

  // fallback: multiple legacy properties with same value, and new config with matching value
  @Test
  void test_7_fallbackMultipleLegacyPropertyAndNewPropertySameValue() {
    helper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setLegacyPropertyValues("legacy.prop1", "sameValue");
    setLegacyPropertyValues("legacy.prop2", "sameValue");
    String result = helper.validateLegacyConfiguration(
        "modern.prop", "sameValue", String.class, true, true);
    assertEquals(result, "sameValue");
  }

  // multiple legacy properties with same value, and new config with conflict -> error
  @Test
  void test_8_fallbackMultipleLegacyPropertyAndNewPropertyDifferentValue() {
    helper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setLegacyPropertyValues("legacy.prop1", "legacyValue");
    setLegacyPropertyValues("legacy.prop2", "legacyValue");
    RuntimeException expectedException = assertThrows(RuntimeException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "newValue", String.class, true, true));
    assertTrue(expectedException.getMessage().contains(PROPERTIES_MUST_BE_REMOVED_SIGNATURE));
  }

  // happy case: new config only
  @Test
  void test_9_newConfigOnly() {
    helper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setLegacyPropertyValues("legacy.prop1", null);
    setLegacyPropertyValues("legacy.prop2", null);
    String result = helper.validateLegacyConfiguration(
        "modern.prop", "newValue", String.class, true, true);
    assertEquals(result, "newValue");
  }

  // misuse: allowLegacy = false, with onlyIfMatch = true
  @Test
  void test_10_booleanMisconfiguration() {
    IllegalArgumentException expectedException = assertThrows(IllegalArgumentException.class, () ->
        helper.validateLegacyConfiguration(
            "doesnt.matter", "doesntMatter", String.class, false, true));
    assertEquals(expectedException.getMessage(), UnifiedConfigurationHelper.WRONG_INPUT_ERROR);
  }

  // legacy config not allowed -> error
  @Test
  void test_11_legacyConfigNotAllowed() {
    helper.setCustomLegacyProperties(MULTIPLE_LEGACY_PROPERTIES);
    setLegacyPropertyValues("legacy.prop1", "legacyValue");
    setLegacyPropertyValues("legacy.prop2", "legacyValue");
    RuntimeException expectedException = assertThrows(RuntimeException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "doesntMatter", String.class, false, false));
    assertTrue(expectedException.getMessage().contains(LEGACY_NOT_ALLOWED_SIGNATURE));
  }
}
