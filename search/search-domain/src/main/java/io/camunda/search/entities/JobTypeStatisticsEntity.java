/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;

/**
 * Represents aggregated job statistics for a specific job type.
 *
 * @param jobType the job type identifier
 * @param created metrics for created jobs
 * @param completed metrics for completed jobs
 * @param failed metrics for failed jobs
 * @param workers number of distinct workers observed for this job type
 */
public record JobTypeStatisticsEntity(
    String jobType,
    StatusMetric created,
    StatusMetric completed,
    StatusMetric failed,
    int workers) {}
