/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import org.slf4j.Logger;

public class ApplyRolloverPeriodJob implements Runnable {

  private final ArchiverRepository repository;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;

  public ApplyRolloverPeriodJob(
      final ArchiverRepository repository,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    this.repository = repository;
    this.metrics = metrics;
    this.logger = logger;
  }

  @Override
  public void run() {
    repository.setLifeCycleToAllIndexes();
  }
}
