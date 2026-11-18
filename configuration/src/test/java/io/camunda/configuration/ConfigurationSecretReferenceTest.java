/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ConfigurationSecretReferenceTest {

  @Test
  void shouldParseAReference() {
    // when
    final var reference = ConfigurationSecretReference.parse("secret:es-password_2");

    // then
    assertThat(reference).contains(new ConfigurationSecretReference("es-password_2"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"secret:", "secret:es password", "secret:es.password", "secret:es/a"})
  void shouldNotParseAMalformedReference(final String value) {
    // when
    final var reference = ConfigurationSecretReference.parse(value);

    // then
    assertThat(ConfigurationSecretReference.isReference(value)).isTrue();
    assertThat(reference).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"password", "my-secret:value", " secret:es-password", "SECRET:x"})
  void shouldNotTakeALiteralForAReference(final String value) {
    // when
    final var isReference = ConfigurationSecretReference.isReference(value);

    // then
    assertThat(isReference).isFalse();
  }
}
