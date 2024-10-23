/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.exporter.api.ExporterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ConfigValidatorTest {
  private static ExporterConfiguration CONFIG = new ExporterConfiguration();

  @BeforeEach
  void reset() {
    CONFIG = new ExporterConfiguration();
  }

  @Test
  void shouldRejectWrongConnectionType() {
    // given
    CONFIG.getConnect().setType("mysql");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(CONFIG)).isInstanceOf(ExporterException.class);
  }

  @Test
  void shouldNotAllowUnderscoreInIndexPrefix() {
    // given
    CONFIG.getIndex().setPrefix("i_am_invalid");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(CONFIG)).isInstanceOf(ExporterException.class);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(ints = {-1, 0})
  void shouldForbidNonPositiveNumberOfShards(final int invalidNumberOfShards) {
    // given
    CONFIG.getIndex().setNumberOfShards(invalidNumberOfShards);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(CONFIG)).isInstanceOf(ExporterException.class);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"1", "-1", "1ms"})
  void shouldNotAllowInvalidMinimumAge(final String invalidMinAge) {
    // given
    CONFIG.getRetention().setMinimumAge(invalidMinAge);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(CONFIG))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("must match pattern '^[0-9]+[dhms]$'")
        .hasMessageContaining("minimumAge '" + invalidMinAge + "'");
  }

  @Test
  void shouldForbidNegativeNumberOfReplicas() {
    // given
    CONFIG.getIndex().setNumberOfReplicas(-1);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(CONFIG)).isInstanceOf(ExporterException.class);
  }
}
