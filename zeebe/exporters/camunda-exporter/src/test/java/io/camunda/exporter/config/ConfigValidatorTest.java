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
  private static ExporterConfiguration config = new ExporterConfiguration();

  @BeforeEach
  void reset() {
    config = new ExporterConfiguration();
  }

  @Test
  void shouldRejectWrongConnectionType() {
    // given
    config.getConnect().setType("mysql");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config)).isInstanceOf(ExporterException.class);
  }

  @Test
  void shouldNotAllowUnderscoreInIndexPrefix() {
    // given
    config.getIndex().setPrefix("i_am_invalid");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config)).isInstanceOf(ExporterException.class);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(ints = {-1, 0})
  void shouldForbidNonPositiveNumberOfShards(final int invalidNumberOfShards) {
    // given
    config.getIndex().setNumberOfShards(invalidNumberOfShards);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config)).isInstanceOf(ExporterException.class);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"1", "-1", "1ms"})
  void shouldNotAllowInvalidMinimumAge(final String invalidMinAge) {
    // given
    config.getRetention().setMinimumAge(invalidMinAge);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("must match pattern '^[0-9]+[dhms]$'")
        .hasMessageContaining("minimumAge '" + invalidMinAge + "'");
  }

  @Test
  void shouldForbidNegativeNumberOfReplicas() {
    // given
    config.getIndex().setNumberOfReplicas(-1);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config)).isInstanceOf(ExporterException.class);
  }

  @Test
  void shouldAssureRolloverIntervalToBeValid() {
    // given
    // Rollover interval must match pattern '%d{timeunit}', where timeunit is one of 'd', 'h',
    // 'm', 's'. A valid rollover interval should be for example "1d" or
    // "1h". Zero or negative time units are not allowed.
    config.getArchiver().setRolloverInterval("1day");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter rolloverInterval '1day' must match pattern '^(?:[1-9]\\d*)([smhdwMy])$', but didn't.");
  }

  @Test
  void shouldAssureWaitPeriodBeforeArchivingToBeValid() {
    // given
    // waitPeriodBeforeArchiving must match pattern '%d{timeunit}', where timeunit is one of 'd',
    // 'h', 'm', 's'.
    config.getArchiver().setWaitPeriodBeforeArchiving("20minutes");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter waitPeriodBeforeArchiving '20minutes' must match pattern '^(?:[1-9]\\d*)([smhdwMy])$', but didn't.");
  }

  @Test
  void shouldForbidRolloverBatchSizeToBeLessThanOne() {
    // given
    config.getArchiver().setRolloverBatchSize(0);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("CamundaExporter rolloverBatchSize must be >= 1. Current value: 0");
  }

  @Test
  void shouldForbidDelayBetweenRunsToBeLessThanOne() {
    // given
    config.getArchiver().setDelayBetweenRuns(0);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("CamundaExporter delayBetweenRuns must be >= 1. Current value: 0");
  }
}
