/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static io.camunda.application.commons.identity.PersistentWebSessionPropertiesPostProcessor.CANONICAL_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PersistentWebSessionPropertiesPostProcessorTest {

  private final PersistentWebSessionPropertiesPostProcessor processor =
      new PersistentWebSessionPropertiesPostProcessor();

  private static StandardEnvironment environmentWith(final Map<String, Object> properties) {
    final var environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "camunda.persistent.sessions.enabled",
        "camunda.operate.persistent.sessions.enabled",
        "camunda.operate.persistentSessionsEnabled",
        "camunda.tasklist.persistent.sessions.enabled",
        "camunda.tasklist.persistentSessionsEnabled"
      })
  void shouldMapLegacyKeyToCanonicalProperty(final String legacyKey) {
    // given
    final var environment = environmentWith(Map.of(legacyKey, "true"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(environment.getProperty(CANONICAL_PROPERTY)).isEqualTo("true");
  }

  @Test
  void shouldMapToFalseWhenLegacyKeyIsFalse() {
    // given
    final var environment =
        environmentWith(Map.of("camunda.operate.persistent.sessions.enabled", "false"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(environment.getProperty(CANONICAL_PROPERTY)).isEqualTo("false");
  }

  @Test
  void shouldEnableWhenAnyLegacyKeyIsTrue() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                "camunda.operate.persistent.sessions.enabled", "false",
                "camunda.persistent.sessions.enabled", "true"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(environment.getProperty(CANONICAL_PROPERTY)).isEqualTo("true");
  }

  @Test
  void shouldNotOverrideExplicitCanonicalProperty() {
    // given
    final var environment =
        environmentWith(
            Map.of(CANONICAL_PROPERTY, "false", "camunda.persistent.sessions.enabled", "true"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(environment.getProperty(CANONICAL_PROPERTY)).isEqualTo("false");
  }

  @Test
  void shouldDoNothingWhenNoLegacyKeysPresent() {
    // given
    final var environment = environmentWith(Map.of("unrelated.property", "value"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(environment.containsProperty(CANONICAL_PROPERTY)).isFalse();
  }
}
