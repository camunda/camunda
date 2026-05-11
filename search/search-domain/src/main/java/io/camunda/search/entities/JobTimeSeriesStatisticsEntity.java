/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Represents aggregated job metrics for a single time bucket.
 *
 * @param time the start timestamp of the time bucket
 * @param created metrics for jobs created within this bucket
 * @param completed metrics for jobs completed within this bucket
 * @param failed metrics for jobs failed within this bucket
 */
public record JobTimeSeriesStatisticsEntity(
    OffsetDateTime time, StatusMetric created, StatusMetric completed, StatusMetric failed) {

  public JobTimeSeriesStatisticsEntity {
    Objects.requireNonNull(time, "time");
    Objects.requireNonNull(created, "created");
    Objects.requireNonNull(completed, "completed");
    Objects.requireNonNull(failed, "failed");
  }
}
