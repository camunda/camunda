/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class UsageMetricClient {

  private static final Function<Long, Record<UsageMetricRecordValue>> SUCCESS_EXPECTATION =
      (sourceRecordPosition) ->
          RecordingExporter.usageMetricsRecords(UsageMetricIntent.EXPORTED)
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();

  private final CommandWriter writer;
  private final UsageMetricRecord usageMetricRecord = new UsageMetricRecord();

  public UsageMetricClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public UsageMetricClient withEventType(final EventType eventType) {
    usageMetricRecord.setEventType(eventType);
    return this;
  }

  public UsageMetricClient withIntervalType(final IntervalType intervalType) {
    usageMetricRecord.setIntervalType(intervalType);
    return this;
  }

  public UsageMetricClient withResetTime(final long resetTime) {
    usageMetricRecord.setResetTime(resetTime);
    return this;
  }

  public Record<UsageMetricRecordValue> export() {
    final var pos = writer.writeCommand(UsageMetricIntent.EXPORT, usageMetricRecord);
    return SUCCESS_EXPECTATION.apply(pos);
  }
}
