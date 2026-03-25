/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.MetricEnum.ERROR_METRIC;
import static io.camunda.optimize.MetricEnum.OVERALL_IMPORT_TIME_METRIC;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class OptimizeMetrics {

  // Import-related tags
  public static final String RECORD_TYPE_TAG = "RECORD_TYPE";
  public static final String PARTITION_ID_TAG = "PARTITION_ID";

  // Report-related tags
  public static final String REPORT_TYPE_TAG = "REPORT_TYPE";
  public static final String REPORT_CATEGORY_TAG = "REPORT_CATEGORY";
  public static final String STATUS_TAG = "STATUS";
  public static final String SAVED_TAG = "SAVED";

  // Error-related tags
  public static final String ERROR_TYPE_TAG = "ERROR_TYPE";

  public static final String METRICS_ENDPOINT = "metrics";

  public static <T extends ZeebeRecordDto<?, ?>> void recordOverallEntitiesImportTime(
      final List<T> entities) {
    final OffsetDateTime currentTime = LocalDateUtil.getCurrentDateTime();
    entities.forEach(
        entity ->
            getTimer(
                    OVERALL_IMPORT_TIME_METRIC,
                    entity.getValueType().name(),
                    entity.getPartitionId())
                .record(
                    currentTime.toInstant().toEpochMilli() - entity.getTimestamp(), MILLISECONDS));
  }

  public static Timer getTimer(
      final MetricEnum metric, final String recordType, final Integer partitionId) {
    return Timer.builder(metric.getName())
        .description(metric.getDescription())
        .tag(RECORD_TYPE_TAG, recordType)
        .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
        .register(Metrics.globalRegistry);
  }

  /**
   * Registers a counter with the given metric definition and tags. Counters are used to track the
   * number of occurrences of an event.
   *
   * @param metricEnum the metric definition from {@link MetricEnum}
   * @param tags the tags to apply to this counter
   * @return a Counter instance registered to the global registry
   */
  public static Counter registerCounter(final MetricEnum metricEnum, final Tags tags) {
    return Counter.builder(metricEnum.getName())
        .description(metricEnum.getDescription())
        .tags(tags)
        .register(Metrics.globalRegistry);
  }

  /**
   * Registers a timer with the given metric definition and tags. Timers are used to track the
   * duration and frequency of events.
   *
   * @param metricEnum the metric definition from {@link MetricEnum}
   * @param tags the tags to apply to this timer
   * @return a Timer instance registered to the global registry
   */
  public static Timer registerTimer(final MetricEnum metricEnum, final Tags tags) {
    return Timer.builder(metricEnum.getName())
        .description(metricEnum.getDescription())
        .tags(tags)
        .register(Metrics.globalRegistry);
  }

  /**
   * Records an error occurrence with the specified error type. The error is tracked as a counter
   * and tagged with the error type for analysis.
   *
   * @param errorType the type of error
   */
  public static void recordError(final String errorType) {
    if (Objects.nonNull(errorType)) {
      registerCounter(ERROR_METRIC, Tags.of(ERROR_TYPE_TAG, errorType)).increment();
    }
  }

  public static <T> T recordLatency(
      final MetricEnum metric,
      final Supplier<ReportDefinitionDto<?>> reportSupplier,
      final Callable<T> action) {
    final long startNanos = System.nanoTime();
    String status = "success";
    try {
      return action.call();
    } catch (final RuntimeException e) {
      status = "error";
      throw e;
    } catch (final Exception e) {
      status = "error";
      throw new RuntimeException(e);
    } finally {
      final long durationMillis = NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      final Tags tags = tagsFor(reportSupplier.get(), status);
      registerTimer(metric, tags).record(durationMillis, MILLISECONDS);
    }
  }

  static Tags buildTags(final ReportDefinitionDto<?> report, final String status) {
    return Tags.of(
        REPORT_CATEGORY_TAG,
        report.isCombined() ? "combined" : "single",
        REPORT_TYPE_TAG,
        Optional.ofNullable(report.getReportType()).map(ReportType::getId).orElse("unknown"),
        SAVED_TAG,
        String.valueOf(report.getId() != null),
        STATUS_TAG,
        status);
  }

  static Tags buildFallbackTags(final String status) {
    return Tags.of(
        REPORT_CATEGORY_TAG, "unknown",
        REPORT_TYPE_TAG, "unknown",
        SAVED_TAG, "false",
        STATUS_TAG, status);
  }

  private static Tags tagsFor(final ReportDefinitionDto<?> report, final String status) {
    return report != null ? buildTags(report, status) : buildFallbackTags(status);
  }
}
