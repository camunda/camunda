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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
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

  private List<UsageMetricRecord> divideRecord(final UsageMetricRecord record) {
    final var size = record.getValues().size();
    final int halfCapacity = (int) Math.ceil(size / 2.0f);
    final var values1 = new HashMap<String, Long>(halfCapacity);
    final var values2 = new HashMap<String, Long>(halfCapacity);

    record
        .getValues()
        .forEach(
            (tenantId, value) -> {
              if (values1.size() < halfCapacity) {
                values1.put(tenantId, value);
              } else {
                values2.put(tenantId, value);
              }
            });

    final var record1 =
        UsageMetricRecord.copyWithoutValues(record)
            .setValues(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(values1)));
    final var record2 =
        UsageMetricRecord.copyWithoutValues(record)
            .setValues(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(values2)));

    final var result = new ArrayList<>(checkRecordLength(record1));
    result.addAll(checkRecordLength(record2));
    return result;
  }

  private List<UsageMetricRecord> checkRecordLength(final UsageMetricRecord record) {
    if (!stateWriter.canWriteEventOfLength(record.getLength())) {
      return divideRecord(record);
    }
    return List.of(record);
  }

  @Override
  public void processRecord(final TypedRecord<UsageMetricRecord> record) {

    final UsageMetricRecord eventRecord =
        new UsageMetricRecord()
            .setIntervalType(IntervalType.ACTIVE)
            .setEventType(EventType.NONE)
            .setResetTime(record.getTimestamp());

    final var bucket = usageMetricState.getActiveBucket();
    if (bucket != null) {
      processMetricType(
          bucket,
          eventRecord,
          EventType.RPI,
          bucket.getTenantRPIMap(),
          bucket.getTenantRPIMapValue());
      processMetricType(
          bucket,
          eventRecord,
          EventType.EDI,
          bucket.getTenantEDIMap(),
          bucket.getTenantEDIMapValue());
    } else {
      checkRecordLength(eventRecord).forEach(this::appendFollowUpEvent);
    }
  }

  /** Processes a specific metric type and appends the resulting records. */
  private void processMetricType(
      final PersistedUsageMetrics bucket,
      final UsageMetricRecord baseRecord,
      final EventType eventType,
      final Map<?, ?> conditionMap,
      final DirectBuffer valuesBuffer) {
    if (!conditionMap.isEmpty()) {
      final UsageMetricRecord clonedRecord = cloneEventRecord(baseRecord);
      enhanceEventRecord(clonedRecord, bucket, eventType, valuesBuffer);
      checkRecordLength(clonedRecord).forEach(this::appendFollowUpEvent);
    }
  }

  /** Creates a deep clone of the given UsageMetricRecord. */
  private UsageMetricRecord cloneEventRecord(final UsageMetricRecord original) {
    return new UsageMetricRecord()
        .setIntervalType(original.getIntervalType())
        .setEventType(original.getEventType())
        .setResetTime(original.getResetTime());
  }

  /** Composes the event record with additional information. */
  private void enhanceEventRecord(
      final UsageMetricRecord record,
      final PersistedUsageMetrics bucket,
      final EventType eventType,
      final DirectBuffer valuesBuffer) {
    record
        .setEventType(eventType)
        .setStartTime(bucket.getFromTime())
        .setEndTime(bucket.getToTime())
        .setValues(valuesBuffer);
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
