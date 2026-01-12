/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.jobmetrics;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.InstantSource;

public class JobMetricsCheckerScheduler implements StreamProcessorLifecycleAware {

  private final JobMetricsChecker jobMetricsChecker;

  public JobMetricsCheckerScheduler(
      final EngineConfiguration engineConfiguration, final InstantSource clock) {
    final var exportInterval = engineConfiguration.getJobMetricsExportInterval();
    jobMetricsChecker = new JobMetricsChecker(exportInterval, clock);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    jobMetricsChecker.setProcessingContext(processingContext);
    jobMetricsChecker.setShouldReschedule(true);
    jobMetricsChecker.schedule(true);
  }

  @Override
  public void onClose() {
    jobMetricsChecker.setShouldReschedule(false);
  }

  @Override
  public void onFailed() {
    jobMetricsChecker.setShouldReschedule(false);
  }

  @Override
  public void onPaused() {
    jobMetricsChecker.setShouldReschedule(false);
  }

  @Override
  public void onResumed() {
    jobMetricsChecker.setShouldReschedule(true);
    jobMetricsChecker.schedule(true);
  }
}
