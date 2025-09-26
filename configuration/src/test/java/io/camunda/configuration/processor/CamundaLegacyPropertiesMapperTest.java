/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.processor;

import static io.camunda.configuration.processor.CamundaLegacyPropertiesMapping.Mode.*;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.processor.CamundaLegacyPropertiesMapping.LegacyProperty;
import io.camunda.configuration.processor.CamundaLegacyPropertiesMapping.LegacyProperty.Mapper;
import io.camunda.configuration.processor.CamundaLegacyPropertiesMapping.Mode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

public class CamundaLegacyPropertiesMapperTest {
  private static ConfigurableEnvironment from(
      final Set<String> profiles, final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    if (profiles != null) {
      env.setActiveProfiles(profiles.toArray(new String[0]));
    }
    for (final Map.Entry<String, String> entry : properties.entrySet()) {
      env.setProperty(entry.getKey(), entry.getValue());
    }
    return env;
  }

  private static ConfigurableEnvironment from(final Map<String, String> properties) {
    return from(null, properties);
  }

  private static Set<String> profiles(final String... profiles) {
    return Set.of(profiles);
  }

  @SafeVarargs
  private static Map<String, String> properties(final Entry<String, String>... entries) {
    return Map.ofEntries(entries);
  }

  private static List<CamundaLegacyPropertiesMapping> from(
      final CamundaLegacyPropertiesMapping... mappings) {
    return Arrays.asList(mappings);
  }

  private static CamundaLegacyPropertiesMapping mapping(
      final String newProperty, final Set<Set<LegacyProperty>> legacyProperties, final Mode mode) {
    return new CamundaLegacyPropertiesMapping(newProperty, legacyProperties, mode);
  }

  private static CamundaLegacyPropertiesMapping mapping(
      final String newProperty, final Set<Set<LegacyProperty>> legacyProperties) {
    return mapping(newProperty, legacyProperties, null);
  }

  @SafeVarargs
  private static Set<Set<LegacyProperty>> legacy(final Set<LegacyProperty>... properties) {
    return Set.of(properties);
  }

  private static Set<LegacyProperty> generation(final LegacyProperty... properties) {
    return Set.of(properties);
  }

  private static LegacyProperty property(
      final String name, final Set<String> profiles, final Mapper mapper) {
    return new LegacyProperty(name, profiles, mapper);
  }

  private static LegacyProperty property(final String name) {
    return property(name, null, null);
  }

  private static LegacyProperty property(final String name, final Set<String> profiles) {
    return property(name, profiles, null);
  }

  private static LegacyProperty property(final String name, final Mapper mapper) {
    return property(name, null, mapper);
  }

  private static CamundaLegacyPropertiesMapper mapper(
      final List<CamundaLegacyPropertiesMapping> mappings) {
    return new CamundaLegacyPropertiesMapper(mock(DeferredLog.class), mappings);
  }

  @Test
  void shouldMapLegacyProperty() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(mapping("new.property", legacy(generation(property("legacy.property")))));
    final ConfigurableEnvironment environment =
        from(properties(entry("legacy.property", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.property")).isTrue();
    assertThat(environment.getProperty("new.property")).isEqualTo("someValue");
  }

  @Test
  void shouldThrowOnNonSupportedLegacyProperty() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(
            mapping("new.property", legacy(generation(property("legacy.property"))), notSupported));
    final ConfigurableEnvironment environment =
        from(properties(entry("legacy.property", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    assertThatThrownBy(() -> mapper.mapProperties(environment))
        .isInstanceOf(UnifiedConfigurationException.class)
        .message()
        .contains("legacy.property")
        .contains("new.property")
        .contains("no longer supported");
  }

  @Test
  void shouldMapLegacyPropertySupportedIfValuesMatch() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(mapping("new.property", legacy(generation(property("legacy.property")))));
    final ConfigurableEnvironment environment =
        from(properties(entry("legacy.property", "someValue"), entry("new.property", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.property")).isTrue();
    assertThat(environment.getProperty("new.property")).isEqualTo("someValue");
  }

  @Test
  void shouldThrowOnSupportedIfValuesMatchLegacyPropertyNull() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(
            mapping(
                "new.property",
                legacy(generation(property("legacy.property"))),
                supportedOnlyIfValuesMatch));
    final ConfigurableEnvironment environment =
        from(properties(entry("legacy.property", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    assertThatThrownBy(() -> mapper.mapProperties(environment))
        .isInstanceOf(UnifiedConfigurationException.class)
        .message()
        .contains("legacy.property")
        .contains("conflicting values");
  }

  @Test
  void shouldThrowOnSupportedIfValuesMatchLegacyProperty() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(
            mapping(
                "new.property",
                legacy(generation(property("legacy.property"))),
                supportedOnlyIfValuesMatch));
    final ConfigurableEnvironment environment =
        from(
            properties(entry("legacy.property", "someValue"), entry("new.property", "someValue2")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    assertThatThrownBy(() -> mapper.mapProperties(environment))
        .isInstanceOf(UnifiedConfigurationException.class)
        .message()
        .contains("legacy.property")
        .contains("conflicting values");
  }

  @Test
  void shouldMapLegacyWildcardNestedProperty() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(mapping("new.property.*", legacy(generation(property("legacy.property.*")))));
    final ConfigurableEnvironment environment =
        from(properties(entry("legacy.property.foo", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.property.foo")).isTrue();
    assertThat(environment.getProperty("new.property.foo")).isEqualTo("someValue");
  }

  @Test
  void shouldNotMapPrefixMatchingLegacyWildcardNestedProperty() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(mapping("new.property.*", legacy(generation(property("legacy.property.*")))));
    final ConfigurableEnvironment environment =
        from(properties(entry("legacy.propertyfoo.foo", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.propertyfoo.foo")).isFalse();
  }

  @Test
  void shouldMapLegacyWildcardListProperty() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(mapping("new.property.*", legacy(generation(property("legacy.property.*")))));
    final ConfigurableEnvironment environment =
        from(properties(entry("legacy.property[0].foo", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.property[0].foo")).isTrue();
    assertThat(environment.getProperty("new.property[0].foo")).isEqualTo("someValue");
  }

  @Test
  void shouldNotMapLegacyPropertyIfProfileNotMatching() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(
            mapping(
                "new.property", legacy(generation(property("legacy.property", profiles("bar"))))));
    final ConfigurableEnvironment environment =
        from(profiles("foo"), properties(entry("legacy.property", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.property")).isFalse();
  }

  @Test
  void shouldMapLegacyPropertyIfProfileMatching() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(
            mapping(
                "new.property", legacy(generation(property("legacy.property", profiles("bar"))))));
    final ConfigurableEnvironment environment =
        from(profiles("bar"), properties(entry("legacy.property", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.property")).isTrue();
    assertThat(environment.getProperty("new.property")).isEqualTo("someValue");
  }

  @Test
  void shouldMapLegacyPropertyIfNegativeProfileNotMatching() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(
            mapping(
                "new.property",
                legacy(generation(property("legacy.property", profiles("!foo", "bar"))))));
    final ConfigurableEnvironment environment =
        from(profiles("bar"), properties(entry("legacy.property", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.property")).isTrue();
    assertThat(environment.getProperty("new.property")).isEqualTo("someValue");
  }

  @Test
  void shouldNotMapLegacyPropertyIfNegativeProfileMatching() {
    final List<CamundaLegacyPropertiesMapping> mappings =
        from(
            mapping(
                "new.property",
                legacy(generation(property("legacy.property", profiles("!foo", "bar"))))));
    final ConfigurableEnvironment environment =
        from(profiles("foo"), properties(entry("legacy.property", "someValue")));
    final CamundaLegacyPropertiesMapper mapper = mapper(mappings);
    mapper.mapProperties(environment);
    assertThat(environment.containsProperty("new.property")).isFalse();
  }
}
