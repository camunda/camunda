/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

  @Autowired private Metrics metrics;

  @Override
  public void finished(final ImportBatch importBatch) {
    metrics.recordCounts(
        Metrics.COUNTER_NAME_EVENTS_PROCESSED,
        importBatch.getRecordsCount(),
        Metrics.TAG_KEY_TYPE,
        importBatch.getImportValueType().toString(),
        Metrics.TAG_KEY_PARTITION,
        String.valueOf(importBatch.getPartitionId()),
        Metrics.TAG_KEY_STATUS,
        Metrics.TAG_VALUE_SUCCEEDED);
    if (importBatch.getImportValueType() == ImportValueType.PROCESS_INSTANCE) {
      metrics.recordCounts(
          Metrics.COUNTER_NAME_EVENTS_PROCESSED_FINISHED_WI, importBatch.getFinishedWiCount());
    }
    if (importBatch.getScheduledTime() != null) {
      metrics
          .getTimer(
              TIMER_NAME_IMPORT_PROCESS_BATCH,
              Metrics.TAG_KEY_TYPE,
              importBatch.getImportValueType().name(),
              Metrics.TAG_KEY_PARTITION,
              String.valueOf(importBatch.getPartitionId()))
          .record(Duration.between(importBatch.getScheduledTime(), OffsetDateTime.now()));
    }
  }

  @Override
  public void failed(final ImportBatch importBatch) {
    metrics.recordCounts(
        Metrics.COUNTER_NAME_EVENTS_PROCESSED,
        importBatch.getRecordsCount(),
        Metrics.TAG_KEY_TYPE,
        importBatch.getImportValueType().toString(),
        Metrics.TAG_KEY_PARTITION,
        String.valueOf(importBatch.getPartitionId()),
        Metrics.TAG_KEY_STATUS,
        Metrics.TAG_VALUE_FAILED);
    if (importBatch.getScheduledTime() != null) {
      metrics
          .getTimer(
              TIMER_NAME_IMPORT_PROCESS_BATCH,
              Metrics.TAG_KEY_TYPE,
              importBatch.getImportValueType().name(),
              Metrics.TAG_KEY_PARTITION,
              String.valueOf(importBatch.getPartitionId()))
          .record(Duration.between(importBatch.getScheduledTime(), OffsetDateTime.now()));
    }
  }
}
