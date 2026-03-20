/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class ReportMetrics {
  public static final String REPORT_TYPE_TAG = "REPORT_TYPE";
  public static final String REPORT_CATEGORY_TAG = "REPORT_CATEGORY";
  public static final String STATUS_TAG = "STATUS";
  public static final String SAVED_TAG = "SAVED";

  private ReportMetrics() {}

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
      Timer.builder(metric.getName())
          .description(metric.getDescription())
          .tags(tags)
          .register(Metrics.globalRegistry)
          .record(durationMillis, MILLISECONDS);
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
