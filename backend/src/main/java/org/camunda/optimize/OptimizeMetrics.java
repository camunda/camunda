/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.experimental.UtilityClass;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;

import java.time.OffsetDateTime;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.camunda.optimize.MetricEnum.OVERALL_IMPORT_TIME_METRIC;

@UtilityClass
public class OptimizeMetrics {
  public static final String RECORD_TYPE_TAG = "RECORD_TYPE";
  public static final String PARTITION_ID_TAG = "PARTITION_ID";
  public static final String METRICS_ENDPOINT = "metrics";


  public static <T extends ZeebeRecordDto<?, ?>> void recordOverallEntitiesImportTime(List<T> entities) {
    OffsetDateTime currentTime = LocalDateUtil.getCurrentDateTime();
    entities.forEach(entity -> getTimer(
      OVERALL_IMPORT_TIME_METRIC,
      entity.getValueType().name(),
      entity.getPartitionId()
    ).record(
      currentTime.toInstant().toEpochMilli() - entity.getTimestamp(),
      MILLISECONDS
    ));
  }

  public static Timer getTimer(MetricEnum metric, String recordType, Integer partitionId) {
    return Timer
      .builder(metric.getName())
      .description(metric.getDescription())
      .tag(RECORD_TYPE_TAG, recordType)
      .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
      .register(Metrics.globalRegistry);
  }
}
