package io.camunda.configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

class UnifiedConfigurationHelperTest {

  UnifiedConfigurationHelper helper;
  Environment mockEnvironment;

  // Example legacy properties map
  static final Map<String, Set<String>> LEGACY_PROPERTIES = Map.of(
      "modern.prop", Set.of("legacy.prop1", "legacy.prop2")
  );

  @BeforeEach
  void setup() {
    mockEnvironment = mock(Environment.class);
    helper = new UnifiedConfigurationHelper(mockEnvironment);
    helper.setCustomLegacyProperties(LEGACY_PROPERTIES);
  }

  // Helper method to setup environment returns for legacy properties
  private void setLegacyPropertyValues(Map<String, String> values) {
    values.forEach((key, val) -> Mockito.when(mockEnvironment.getProperty(key)).thenReturn(val));
  }

  // --- TEST CASES ---

  @Test
  void throwsIfOnlyIfValuesMatchButLegacyNotAllowed() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "newVal", String.class, false, true));

    assertEquals(UnifiedConfigurationHelper.WRONG_INPUT_ERROR, ex.getMessage());
  }

  @Test
  void throwsIfModernPropertyNotInLegacyMap() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        helper.validateLegacyConfiguration(
            "unknown.prop", "newVal", String.class, true, false));

    assertTrue(ex.getMessage().contains("unknown.prop"));
  }

  @Test
  void returnsNewValueIfNoLegacyValuesSet() {
    Map<String, String> values = new HashMap<>();
    values.put("legacy.prop1", null);
    values.put("legacy.prop2", null);
    setLegacyPropertyValues(values);

    String result = helper.validateLegacyConfiguration(
        "modern.prop", "newVal", String.class, true, false);

    assertEquals("newVal", result);
  }

  @Test
  void throwsIfLegacyConfigNotAllowedButLegacyValuesPresent() {
    final HashMap<String, String> values = new HashMap<>();
    values.put("legacy.prop1", "oldVal");
    values.put("legacy.prop2", null);
    setLegacyPropertyValues(values);

    RuntimeException ex = assertThrows(RuntimeException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "newVal", String.class, false, false));

    assertTrue(ex.getMessage().contains("legacy.prop1"));
    assertTrue(ex.getMessage().contains("legacy.prop2"));
  }

  @Test
  void throwsIfMultipleLegacyValuesDiffer() {
    setLegacyPropertyValues(Map.of(
        "legacy.prop1", "oldVal1",
        "legacy.prop2", "oldVal2"
    ));

    RuntimeException ex = assertThrows(RuntimeException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "newVal", String.class, true, false));

    assertTrue(ex.getMessage().contains("legacy.prop1"));
    assertTrue(ex.getMessage().contains("oldVal1"));
    assertTrue(ex.getMessage().contains("oldVal2"));
  }

  @Test
  void throwsIfOnlyIfValuesMatchAndValuesDiffer() {
    setLegacyPropertyValues(Map.of(
        "legacy.prop1", "oldVal"
    ));

    RuntimeException ex = assertThrows(RuntimeException.class, () ->
        helper.validateLegacyConfiguration(
            "modern.prop", "newVal", String.class, true, true));

    assertTrue(ex.getMessage().contains("legacy.prop1"));
    assertTrue(ex.getMessage().contains("modern.prop"));
  }

  @Test
  void returnsLegacyValueAndLogsIfLegacySetAndNewUnset() {
    setLegacyPropertyValues(Map.of(
        "legacy.prop1", "oldVal"
    ));

    // newValue is null => unset
    String result = helper.validateLegacyConfiguration(
        "modern.prop", null, String.class, true, false);

    assertEquals("oldVal", result);
    // Ideally check logDeprecationMessage called - requires spying or logging capture
  }

  @Test
  void returnsNewValueIfLegacySetAndNewSet() {
    setLegacyPropertyValues(Map.of(
        "legacy.prop1", "oldVal"
    ));

    String result = helper.validateLegacyConfiguration(
        "modern.prop", "newVal", String.class, true, false);

    assertEquals("newVal", result);
  }

  @Test
  void returnsNewValueIfLegacyConfigNotAllowedAndNoLegacyPropertiesSet() {
    // legacy config not allowed but no legacy values set -> returns newValue
    Map<String, String> values = new HashMap<>();
    values.put("legacy.prop1", null);
    values.put("legacy.prop2", null);
    setLegacyPropertyValues(values);

    String result = helper.validateLegacyConfiguration(
        "modern.prop", "newVal", String.class, false, false);

    assertEquals("newVal", result);
  }

  @Test
  void returnsLegacyValueIfLegacyConfigAllowedAndMultipleLegacyPropertiesHaveSameValue() {
    // legacy config allowed, multiple legacy properties with same value -> return legacyValue
    setLegacyPropertyValues(Map.of(
        "legacy.prop1", "sameVal",
        "legacy.prop2", "sameVal"
    ));

    String result = helper.validateLegacyConfiguration(
        "modern.prop", "", String.class, true, false);

    assertEquals("sameVal", result);
  }

  @Test
  void returnsNewValueIfNoLegacyPropertiesSetAndNewValueIsNull() {
    // no legacy values set, newValue is null => return newValue (null)
    Map<String, String> values = new HashMap<>();
    values.put("legacy.prop1", "");
    values.put("legacy.prop2", "");
    setLegacyPropertyValues(values);

    String result = helper.validateLegacyConfiguration(
        "modern.prop", null, String.class, true, false);

    assertNull(result);
  }

  @Test
  void returnsNewValueIfLegacyConfigAllowedAndLegacyValueSetAndNewValueSet() {
    // legacy config allowed, legacy value set and newValue set => return newValue
    setLegacyPropertyValues(Map.of(
        "legacy.prop1", "legacyVal"
    ));

    String result = helper.validateLegacyConfiguration(
        "modern.prop", "newVal", String.class, true, false);

    assertEquals("newVal", result);
  }

  @Test
  void returnsNewValueIfOnlyIfValuesMatchIsFalseAndLegacyValueDiffersFromNewValue() {
    // onlyIfValuesMatch == false, legacy value differs from newValue => return newValue
    setLegacyPropertyValues(Map.of(
        "legacy.prop1", "legacyVal"
    ));

    String result = helper.validateLegacyConfiguration(
        "modern.prop", "differentVal", String.class, true, false);

    assertEquals("differentVal", result);
  }

  @Test
  void returnsNewValueIfLegacyPropertiesContainNullOrEmptyValues() {
    // legacy config allowed, legacy properties have null/empty values => ignore and return newValue
    Map<String, String> values = new HashMap<>();
    values.put("legacy.prop1", null);
    values.put("legacy.prop2", "");
    setLegacyPropertyValues(values);

    String result = helper.validateLegacyConfiguration(
        "modern.prop", "newVal", String.class, true, false);

    assertEquals("newVal", result);
  }
}
