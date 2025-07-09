/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.InstantSource;

public class UsageMetricsCheckerScheduler implements StreamProcessorLifecycleAware {

  private final UsageMetricsChecker usageMetricsChecker;
  private final boolean enableUsageMetrics;

  public UsageMetricsCheckerScheduler(
      final EngineConfiguration engineConfiguration,
      final InstantSource clock,
      final boolean enableUsageMetrics) {
    final var exportInterval = engineConfiguration.getUsageMetricsExportInterval();
    usageMetricsChecker = new UsageMetricsChecker(exportInterval, clock);
    this.enableUsageMetrics = enableUsageMetrics;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    if (!enableUsageMetrics) {
      return;
    }

    usageMetricsChecker.setProcessingContext(processingContext);
    usageMetricsChecker.setShouldReschedule(true);
    usageMetricsChecker.schedule(true);
  }

  @Override
  public void onClose() {
    usageMetricsChecker.setShouldReschedule(false);
  }

  @Override
  public void onFailed() {
    usageMetricsChecker.setShouldReschedule(false);
  }

  @Override
  public void onPaused() {
    usageMetricsChecker.setShouldReschedule(false);
  }

  @Override
  public void onResumed() {
    if (!enableUsageMetrics) {
      return;
    }

    usageMetricsChecker.setShouldReschedule(true);
    usageMetricsChecker.schedule(true);
  }
}
