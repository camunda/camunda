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
import io.camunda.zeebe.engine.state.immutable.UsageMetricState;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public class UsageMetricsExportProcessor implements TypedRecordProcessor<UsageMetricRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricsExportProcessor.class);

  private final UsageMetricState usageMetricState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final InstantSource clock;

  public UsageMetricsExportProcessor(
      final UsageMetricState usageMetricState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final InstantSource clock) {
    this.usageMetricState = usageMetricState;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<UsageMetricRecord> usageMetricRecord) {

    final long now = clock.millis();

    // bucket is initialized when some metric is recorded or when the first applier runs
    var bucket = usageMetricState.getActiveBucket();
    if (bucket == null) {
      bucket = new PersistedUsageMetrics();
    }

    // Copy and update the bucket toTime as we are exporting it now.
    // Additionally, ensure the fromTime is set. This can happen if metrics were recorded before the
    // first applier.
    final PersistedUsageMetrics finalBucket = bucket.close(now);

    // export usage metric events if there is data
    Stream.of(EventType.RPI, EventType.EDI, EventType.TU)
        .map(eventType -> createUsageMetricRecord(eventType, finalBucket))
        .filter(UsageMetricRecord::hasData)
        .forEach(this::appendFollowUpEvent);
    // append NONE event that triggers the reset in the applier
    appendFollowUpEvent(new UsageMetricRecord().setEventType(EventType.NONE).setResetTime(now));
  }

  private static UsageMetricRecord createUsageMetricRecord(
      final EventType eventType, final PersistedUsageMetrics bucket) {

    final var record =
        new UsageMetricRecord()
            .setStartTime(bucket.getFromTime())
            .setEndTime(bucket.getToTime())
            .setEventType(eventType);

    switch (eventType) {
      case RPI -> record.setCounterValues(bucket.getTenantRPIMapValue());
      case EDI -> record.setCounterValues(bucket.getTenantEDIMapValue());
      case TU -> record.setSetValues(bucket.getTenantTUMapValue());
      default -> {}
    }

    return record;
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
