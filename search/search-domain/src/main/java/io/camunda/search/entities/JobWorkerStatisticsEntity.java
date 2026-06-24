/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import java.util.Objects;

/**
 * Represents aggregated job statistics for a specific worker within a job type.
 *
 * @param worker the worker identifier
 * @param created metrics for created jobs
 * @param completed metrics for completed jobs
 * @param failed metrics for failed jobs
 */
public record JobWorkerStatisticsEntity(
    String worker, StatusMetric created, StatusMetric completed, StatusMetric failed) {

  public JobWorkerStatisticsEntity {
    Objects.requireNonNull(worker, "worker");
    Objects.requireNonNull(created, "created");
    Objects.requireNonNull(completed, "completed");
    Objects.requireNonNull(failed, "failed");
  }
}
