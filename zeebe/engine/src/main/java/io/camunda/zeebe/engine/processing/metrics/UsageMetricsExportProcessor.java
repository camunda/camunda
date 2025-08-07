/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public class UsageMetricsExportProcessor implements TypedRecordProcessor<UsageMetricRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricsExportProcessor.class);

  private final MutableUsageMetricState usageMetricState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public UsageMetricsExportProcessor(
      final MutableUsageMetricState usageMetricState,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    this.usageMetricState = usageMetricState;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<UsageMetricRecord> usageMetricRecord) {

    final UsageMetricRecord eventRecord =
        new UsageMetricRecord()
            .setIntervalType(IntervalType.ACTIVE)
            .setEventType(EventType.NONE)
            .setResetTime(usageMetricRecord.getTimestamp());

    final var bucket = usageMetricState.getActiveBucket();
    if (bucket == null || !bucket.isInitialized()) {
      appendFollowUpEvent(eventRecord);
      return;
    }

    final var isRPIMapEmpty = bucket.getTenantRPIMap().isEmpty();
    final var isEDIMapEmpty = bucket.getTenantEDIMap().isEmpty();
    final var isTUMapEmpty = bucket.getTenantTUMap().isEmpty();

    if (!isRPIMapEmpty || !isEDIMapEmpty || !isTUMapEmpty) {
      processMetricType(
          bucket, eventRecord, EventType.RPI, isRPIMapEmpty, bucket.getTenantRPIMapValue());
      processMetricType(
          bucket, eventRecord, EventType.EDI, isEDIMapEmpty, bucket.getTenantEDIMapValue());
      processMetricType(
          bucket, eventRecord, EventType.TU, isTUMapEmpty, bucket.getTenantTUMapValue());
    } else {
      appendFollowUpEvent(eventRecord);
    }
  }

  /** Processes a specific metric type and appends the resulting records. */
  private void processMetricType(
      final PersistedUsageMetrics bucket,
      final UsageMetricRecord baseRecord,
      final EventType eventType,
      final boolean valuesMapIsEmpty,
      final DirectBuffer valuesBuffer) {
    if (!valuesMapIsEmpty) {
      final UsageMetricRecord clonedRecord = initializeEventRecord(baseRecord);
      enhanceEventRecord(clonedRecord, bucket, eventType, valuesBuffer);
      appendFollowUpEvent(clonedRecord);
    }
  }

  /** Creates a UsageMetricRecord with original properties. */
  private UsageMetricRecord initializeEventRecord(final UsageMetricRecord original) {
    return new UsageMetricRecord()
        .setIntervalType(original.getIntervalType())
        .setEventType(original.getEventType())
        .setResetTime(original.getResetTime());
  }

  /** Composes the event record with additional information. */
  private void enhanceEventRecord(
      final UsageMetricRecord usageMetricRecord,
      final PersistedUsageMetrics bucket,
      final EventType eventType,
      final DirectBuffer valuesBuffer) {
    usageMetricRecord
        .setEventType(eventType)
        .setStartTime(bucket.getFromTime())
        .setEndTime(bucket.getToTime());
    if (eventType == EventType.TU) {
      usageMetricRecord.setSetValues(valuesBuffer);
    } else {
      usageMetricRecord.setCounterValues(valuesBuffer);
    }
  }

  private void appendFollowUpEvent(final UsageMetricRecord eventRecord) {
    LOG.debug(
        "Creating usage metric EXPORTED event for {} {}",
        eventRecord.getStartTime(),
        eventRecord.getEventType());
    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), UsageMetricIntent.EXPORTED, eventRecord);
  }
}
