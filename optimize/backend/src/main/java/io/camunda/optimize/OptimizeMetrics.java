/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.MetricEnum.OVERALL_IMPORT_TIME_METRIC;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.List;

public final class OptimizeMetrics {

  public static final String RECORD_TYPE_TAG = "RECORD_TYPE";
  public static final String PARTITION_ID_TAG = "PARTITION_ID";
  public static final String METRICS_ENDPOINT = "metrics";

  private OptimizeMetrics() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

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
}
