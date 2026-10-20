/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class KebabCasePropertyKeysTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("propertyKeys")
  void shouldValidatePropertyKeys(final String propertyKey, final boolean expectedValid) {
    // when
    final boolean isValid = KebabCasePropertyKeys.isValid(propertyKey);

    // then
    assertThat(isValid)
        .as("'%s' expected to be %s", propertyKey, expectedValid ? "valid" : "rejected")
        .isEqualTo(expectedValid);
  }

  private static Stream<Arguments> propertyKeys() {
    return Stream.of(
        Arguments.of(
            named("canonical kebab-case is accepted", "camunda.data.wait-states.enabled"), true),
        Arguments.of(named("single-segment key is accepted", "port"), true),
        Arguments.of(named("null value is rejected", null), false),
        Arguments.of(named("blank value is rejected", "   "), false),
        Arguments.of(named("empty value is rejected", ""), false),
        Arguments.of(named("uppercase characters are rejected", "camunda.Feature.enabled"), false),
        Arguments.of(named("snake_case is rejected", "camunda_feature_enabled"), false),
        Arguments.of(named("UPPER_SNAKE_CASE is rejected", "CAMUNDA_FEATURE_ENABLED"), false),
        Arguments.of(named("special character is rejected", "camunda.feature.enabled@v2"), false),
        Arguments.of(named("whitespace within key is rejected", "camunda.feature enabled"), false));
  }
}
