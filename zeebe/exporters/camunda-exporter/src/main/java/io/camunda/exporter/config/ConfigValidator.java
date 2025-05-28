/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.config;

import io.camunda.zeebe.exporter.api.ExporterException;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ConfigValidator {
  /**
   * Supported pattern for min_age property of ILM, we only support: days, hours, minutes and
   * seconds. Everything below seconds we don't expect as useful.
   *
   * <p>See reference
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/api-conventions.html#time-units
   */
  private static final String PATTERN_MIN_AGE_FORMAT = "^[0-9]+[dhms]$";

  private static final String PATTERN_DATE_INTERVAL_FORMAT = "^(?:[1-9]\\d*)([smhdwMy])$";

  private static final Predicate<String> CHECKER_MIN_AGE =
      Pattern.compile(PATTERN_MIN_AGE_FORMAT).asPredicate();

  private static final Predicate<String> CHECK_DATE_INTERVAL =
      Pattern.compile(PATTERN_DATE_INTERVAL_FORMAT).asPredicate();

  private ConfigValidator() {}

  public static void validate(final ExporterConfiguration configuration) {
    try {
      ConnectionTypes.from(configuration.getConnect().getType());
    } catch (final IllegalArgumentException e) {
      throw new ExporterException(
          String.format(
              "CamundaExporter connect.type must be one of the supported types '%s', but was: '%s'.",
              Arrays.toString(ConnectionTypes.values()), configuration.getConnect().getType()),
          e);
    }

    if (configuration.getConnect().getIndexPrefix() != null
        && configuration.getConnect().getIndexPrefix().contains("_")) {
      throw new ExporterException(
          String.format(
              "CamundaExporter index.prefix must not contain underscore. Current value: %s",
              configuration.getConnect().getIndexPrefix()));
    }

    final Integer numberOfShards = configuration.getIndex().getNumberOfShards();
    if (numberOfShards != null && numberOfShards < 1) {
      throw new ExporterException(
          String.format(
              "CamundaExporter index.numberOfShards must be >= 1. Current value: %d",
              numberOfShards));
    }

    final Integer numberOfReplicas = configuration.getIndex().getNumberOfReplicas();
    if (numberOfReplicas != null && numberOfReplicas < 0) {
      throw new ExporterException(
          String.format(
              "CamundaExporter index.numberOfReplicas must be >= 0. Current value: %d",
              numberOfReplicas));
    }

    final String minimumAge = configuration.getHistory().getRetention().getMinimumAge();
    if (minimumAge != null && !CHECKER_MIN_AGE.test(minimumAge)) {
      throw new ExporterException(
          String.format(
              "CamundaExporter retention.minimumAge '%s' must match pattern '%s', but didn't.",
              minimumAge, PATTERN_MIN_AGE_FORMAT));
    }

    final String rolloverInterval = configuration.getHistory().getRolloverInterval();
    if (rolloverInterval != null && !CHECK_DATE_INTERVAL.test(rolloverInterval)) {
      throw new ExporterException(
          String.format(
              "CamundaExporter archiver.rolloverInterval '%s' must match pattern '%s', but didn't.",
              rolloverInterval, PATTERN_DATE_INTERVAL_FORMAT));
    }

    final String waitPeriodBeforeArchiving =
        configuration.getHistory().getWaitPeriodBeforeArchiving();
    if (waitPeriodBeforeArchiving != null && !CHECK_DATE_INTERVAL.test(waitPeriodBeforeArchiving)) {
      throw new ExporterException(
          String.format(
              "CamundaExporter archiver.waitPeriodBeforeArchiving '%s' must match pattern '%s', but didn't.",
              waitPeriodBeforeArchiving, PATTERN_DATE_INTERVAL_FORMAT));
    }

    final int rolloverBatchSize = configuration.getHistory().getRolloverBatchSize();
    if (rolloverBatchSize < 1) {
      throw new ExporterException(
          "CamundaExporter archiver.rolloverBatchSize must be >= 1. Current value: "
              + rolloverBatchSize);
    }

    final int delayBetweenRuns = configuration.getHistory().getDelayBetweenRuns();
    if (delayBetweenRuns < 1) {
      throw new ExporterException(
          "CamundaExporter archiver.delayBetweenRuns must be >= 1. Current value: "
              + delayBetweenRuns);
    }

    final int processCacheMaxCacheSize = configuration.getProcessCache().getMaxCacheSize();
    if (processCacheMaxCacheSize < 1) {
      throw new ExporterException(
          "CamundaExporter processCache.maxCacheSize must be >= 1. Current value: "
              + processCacheMaxCacheSize);
    }

    final int formCacheMaxCacheSize = configuration.getFormCache().getMaxCacheSize();
    if (formCacheMaxCacheSize < 1) {
      throw new ExporterException(
          "CamundaExporter maxCacheSize must be >= 1. Current value: " + formCacheMaxCacheSize);
    }
  }
}
