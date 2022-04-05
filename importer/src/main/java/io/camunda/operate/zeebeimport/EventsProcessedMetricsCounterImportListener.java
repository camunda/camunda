/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.Metrics.TIMER_NAME_IMPORT_PROCESS_BATCH;

import io.camunda.operate.Metrics;
import io.camunda.operate.zeebe.ImportValueType;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EventsProcessedMetricsCounterImportListener implements ImportListener {
  
  @Autowired
  private Metrics metrics;

  @Override
  public void finished(ImportBatch importBatch) {
    metrics.recordCounts(Metrics.COUNTER_NAME_EVENTS_PROCESSED, importBatch.getRecordsCount(),
        Metrics.TAG_KEY_TYPE, importBatch.getImportValueType().toString(), Metrics.TAG_KEY_PARTITION, String.valueOf(importBatch.getPartitionId()), Metrics.TAG_KEY_STATUS, Metrics.TAG_VALUE_SUCCEEDED);
    if (importBatch.getImportValueType() == ImportValueType.PROCESS_INSTANCE) {
      metrics.recordCounts(Metrics.COUNTER_NAME_EVENTS_PROCESSED_FINISHED_WI, importBatch.getFinishedWiCount());
    }
    if (importBatch.getScheduledTime() != null) {
      metrics.getTimer(TIMER_NAME_IMPORT_PROCESS_BATCH)
          .record(Duration.between(importBatch.getScheduledTime(), OffsetDateTime.now()));
    }
  }

  @Override
  public void failed(ImportBatch importBatch) {
    metrics.recordCounts(Metrics.COUNTER_NAME_EVENTS_PROCESSED, importBatch.getRecordsCount(),
        Metrics.TAG_KEY_TYPE, importBatch.getImportValueType().toString(), Metrics.TAG_KEY_PARTITION, String.valueOf(importBatch.getPartitionId()), Metrics.TAG_KEY_STATUS, Metrics.TAG_VALUE_FAILED);
    if (importBatch.getScheduledTime() != null) {
      metrics.getTimer(TIMER_NAME_IMPORT_PROCESS_BATCH)
          .record(Duration.between(importBatch.getScheduledTime(), OffsetDateTime.now()));
    }
  }

}
