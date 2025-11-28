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

  private final UsageMetricsCheckScheduler usageMetricsCheckScheduler;

  public UsageMetricsCheckerScheduler(
      final EngineConfiguration engineConfiguration, final InstantSource clock) {
    final var exportInterval = engineConfiguration.getUsageMetricsExportInterval();
    usageMetricsCheckScheduler = new UsageMetricsCheckScheduler(exportInterval, clock);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    usageMetricsCheckScheduler.setProcessingContext(processingContext);
    usageMetricsCheckScheduler.setShouldReschedule(true);
    usageMetricsCheckScheduler.schedule(true);
  }

  @Override
  public void onClose() {
    usageMetricsCheckScheduler.setShouldReschedule(false);
  }

  @Override
  public void onFailed() {
    usageMetricsCheckScheduler.setShouldReschedule(false);
  }

  @Override
  public void onPaused() {
    usageMetricsCheckScheduler.setShouldReschedule(false);
  }

  @Override
  public void onResumed() {
    usageMetricsCheckScheduler.setShouldReschedule(true);
    usageMetricsCheckScheduler.schedule(true);
  }
}
