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
import java.time.Duration;
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
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter connect.type must be one of the supported types '[ELASTICSEARCH, OPENSEARCH]', but was: 'mysql'");
  }

  @ParameterizedTest
  @ValueSource(strings = {"prefix", "hyphenated-prefix", "char+prefix"})
  void shouldAllowValidIndexPrefixes(final String testPrefix) {
    // given
    config.getConnect().setIndexPrefix(testPrefix);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"\\", "/", "*", "?", "\"", ">", "<", "|", " ", "_"})
  void shouldNotAllowInvalidCharactersInIndexPrefix(final String testCharacter) {
    // given
    config.getConnect().setIndexPrefix("test-prefix" + testCharacter);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .hasMessageContaining(
            "CamundaExporter index.prefix must not contain invalid characters [\\ / * ? \" < > | space _].")
        .isInstanceOf(ExporterException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {".", "+"})
  void shouldNotAllowInvalidCharactersAtStartOfIndexPrefix(final String testCharacter) {
    // given
    config.getConnect().setIndexPrefix(testCharacter + "test-prefix");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .hasMessageContaining(
            "CamundaExporter index.prefix must not begin with invalid characters [. +].")
        .isInstanceOf(ExporterException.class);
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
    config.getHistory().getRetention().setMinimumAge(invalidMinAge);

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
  void shouldAssureWaitPeriodBeforeArchivingToBeValid() {
    // given
    // waitPeriodBeforeArchiving must match pattern '%d{timeunit}', where timeunit is one of 'd',
    // 'h', 'm', 's'.
    config.getHistory().setWaitPeriodBeforeArchiving("20minutes");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.waitPeriodBeforeArchiving '20minutes' must match pattern '^(?:[1-9]\\d*)([smhdwMy])$', but didn't.");
  }

  @Test
  void shouldForbidRolloverBatchSizeToBeLessThanOne() {
    // given
    config.getHistory().setRolloverBatchSize(0);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.rolloverBatchSize must be >= 1. Current value: 0");
  }

  @Test
  void shouldForbidDelayBetweenRunsToBeLessThanOne() {
    // given
    config.getHistory().setDelayBetweenRuns(0);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.delayBetweenRuns must be >= 1. Current value: 0");
  }

  @Test
  void shouldAssureRolloverIntervalToBeValid() {
    // given
    // Rollover interval must match pattern '%d{timeunit}', where timeunit is one of 'd', 'h',
    // 'm', 's'. A valid rollover interval should be for example "1d" or
    // "1h". Zero or negative time units are not allowed.
    config.getHistory().setRolloverInterval("1day");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.rolloverInterval '1day' must match pattern '^(?:[1-9]\\d*)([hdwMy])$', but didn't.");
  }

  @Test
  void shouldAssureRolloverIsNotInMinutesOrSeconds() {
    // given
    // Rollover interval must match pattern '%d{timeunit}', where timeunit is one of 'd', 'h',
    // 'm', 's'. A valid rollover interval should be for example "1d" or
    // "1h". Zero or negative time units are not allowed.
    config.getHistory().setRolloverInterval("1m");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.rolloverInterval '1m' must match pattern '^(?:[1-9]\\d*)([hdwMy])$', but didn't.");

    config.getHistory().setRolloverInterval("1s");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.rolloverInterval '1s' must match pattern '^(?:[1-9]\\d*)([hdwMy])$', but didn't.");
  }

  @Test
  void shouldAssureUsageMetricsRolloverIntervalToBeValid() {
    // given
    config.getHistory().setUsageMetricsRolloverInterval("1day");

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.usageMetricsRolloverInterval '1day' must match pattern '^(?:[1-4]w|1M)$', but didn't.");
  }

  @Test
  void shouldAssureUsageMetricsRolloverIsInAllowedRange() {
    // given
    config.getHistory().setUsageMetricsRolloverInterval("7d");
    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.usageMetricsRolloverInterval '7d' must match pattern '^(?:[1-4]w|1M)$', but didn't.");

    // given
    config.getHistory().setUsageMetricsRolloverInterval("5w");
    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.usageMetricsRolloverInterval '5w' must match pattern '^(?:[1-4]w|1M)$', but didn't.");

    // given
    config.getHistory().setUsageMetricsRolloverInterval("2M");
    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter archiver.usageMetricsRolloverInterval '2M' must match pattern '^(?:[1-4]w|1M)$', but didn't.");

    // given
    config.getHistory().setUsageMetricsRolloverInterval("1w");
    // when - then
    ConfigValidator.validate(config);

    // given
    config.getHistory().setUsageMetricsRolloverInterval("4w");
    // when - then
    ConfigValidator.validate(config);

    // given
    config.getHistory().setUsageMetricsRolloverInterval("1M");
    // when - then
    ConfigValidator.validate(config);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(ints = {-1, 0})
  void shouldForbidNonPositiveMaxCacheSize(final int maxCacheSize) {
    // given
    config.getProcessCache().setMaxCacheSize(maxCacheSize);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("CamundaExporter processCache.maxCacheSize must be >= 1.");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(ints = {-1, 0})
  void shouldForbidNonPositiveMaxBatchOperationCacheSize(final int maxCacheSize) {
    // given
    config.getBatchOperationCache().setMaxCacheSize(maxCacheSize);

    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining("CamundaExporter batchOperationCache.maxCacheSize must be >= 1.");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"-PT1M", "PT0S"})
  void shouldForbidNonStrictlyPositiveApplyPolicyJobInterval(
      final Duration applyPolicyJobInterval) {
    // given
    config.getHistory().getRetention().setApplyPolicyJobInterval(applyPolicyJobInterval);
    // when - then
    assertThatCode(() -> ConfigValidator.validate(config))
        .isInstanceOf(ExporterException.class)
        .hasMessageContaining(
            "CamundaExporter retention.applyPolicyJobInterval must be strictly positive.");
  }
}
