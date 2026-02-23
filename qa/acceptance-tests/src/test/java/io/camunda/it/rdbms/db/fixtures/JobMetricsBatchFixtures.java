/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import static java.time.ZoneOffset.UTC;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel;
import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel.Builder;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

public final class JobMetricsBatchFixtures extends CommonFixtures {
  public static final OffsetDateTime NOW = OffsetDateTime.now(UTC).truncatedTo(ChronoUnit.MILLIS);
  public static final int PARTITION_ID = 10;

  private JobMetricsBatchFixtures() {}

  public static JobMetricsBatchDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var startTime = NOW.minus(RANDOM.nextInt(100), ChronoUnit.MINUTES);
    final var endTime = startTime.plus(RANDOM.nextInt(10) + 1, ChronoUnit.MINUTES);
    final var lastCreatedAt =
        startTime.plus(
            RANDOM.nextInt((int) ChronoUnit.MINUTES.between(startTime, endTime) + 1),
            ChronoUnit.MINUTES);
    final var lastCompletedAt =
        startTime.plus(
            RANDOM.nextInt((int) ChronoUnit.MINUTES.between(startTime, endTime) + 1),
            ChronoUnit.MINUTES);
    final var lastFailedAt =
        startTime.plus(
            RANDOM.nextInt((int) ChronoUnit.MINUTES.between(startTime, endTime) + 1),
            ChronoUnit.MINUTES);

    final var builder =
        new Builder()
            .key(nextStringKey())
            .partitionId(PARTITION_ID)
            .startTime(startTime)
            .endTime(endTime)
            .tenantId("tenant-" + RANDOM.nextInt(1000))
            .jobType("jobType-" + RANDOM.nextInt(100))
            .worker("worker-" + RANDOM.nextInt(50))
            .createdCount(RANDOM.nextInt(100))
            .lastCreatedAt(lastCreatedAt)
            .completedCount(RANDOM.nextInt(100))
            .lastCompletedAt(lastCompletedAt)
            .failedCount(RANDOM.nextInt(50))
            .lastFailedAt(lastFailedAt)
            .incompleteBatch(RANDOM.nextBoolean());

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveMetric(
      final RdbmsWriters rdbmsWriters, final JobMetricsBatchDbModel metric) {
    createAndSaveMetrics(rdbmsWriters, List.of(metric));
  }

  public static void createAndSaveMetrics(
      final RdbmsWriters rdbmsWriters, final List<JobMetricsBatchDbModel> metrics) {
    for (final JobMetricsBatchDbModel metric : metrics) {
      rdbmsWriters.getJobMetricsBatchWriter().create(metric);
    }
    rdbmsWriters.flush();
  }
}
