/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.COMMIT_LATENCY;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.CURRENT_INFLIGHT;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.CURRENT_LIMIT;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.LAST_COMMITTED_POSITION;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.LAST_WRITTEN_POSITION;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.RECORD_APPENDED;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.RecordAppendedKeyNames.INTENT;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.RecordAppendedKeyNames.RECORD_TYPE;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.RecordAppendedKeyNames.VALUE_TYPE;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.TOTAL_APPEND_TRY_COUNT;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.TOTAL_DEFERRED_APPEND_COUNT;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.AppendMetricsDoc.WRITE_LATENCY;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.collection.Map3D;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;

public final class AppenderMetrics {

  private final Map3D<RecordType, ValueType, Intent, Counter> recordAppended = Map3D.simple();
  private final AtomicLong inflightAppends = new AtomicLong();
  private final AtomicLong inflightLimit = new AtomicLong();
  private final AtomicLong lastCommitted = new AtomicLong();
  private final AtomicLong lastWritten = new AtomicLong();

  private final MeterRegistry registry;
  private final Counter deferredAppends;
  private final Counter triedAppends;
  private final Timer commitLatency;
  private final Timer appendLatency;

  public AppenderMetrics(final MeterRegistry meterRegistry) {
    registry = meterRegistry;
    deferredAppends =
        Counter.builder(TOTAL_DEFERRED_APPEND_COUNT.getName())
            .description(TOTAL_DEFERRED_APPEND_COUNT.getDescription())
            .register(registry);
    triedAppends =
        Counter.builder(TOTAL_APPEND_TRY_COUNT.getName())
            .description(TOTAL_APPEND_TRY_COUNT.getDescription())
            .register(registry);
    commitLatency =
        Timer.builder(COMMIT_LATENCY.getName())
            .description(COMMIT_LATENCY.getDescription())
            .serviceLevelObjectives(COMMIT_LATENCY.getTimerSLOs())
            .register(registry);
    appendLatency =
        Timer.builder(WRITE_LATENCY.getName())
            .description(WRITE_LATENCY.getDescription())
            .serviceLevelObjectives(WRITE_LATENCY.getTimerSLOs())
            .register(registry);

    Gauge.builder(CURRENT_INFLIGHT.getName(), inflightAppends, Number::longValue)
        .description(CURRENT_INFLIGHT.getDescription())
        .register(registry);
    Gauge.builder(CURRENT_LIMIT.getName(), inflightLimit, Number::longValue)
        .description(CURRENT_LIMIT.getDescription())
        .register(registry);
    Gauge.builder(LAST_COMMITTED_POSITION.getName(), lastCommitted, Number::longValue)
        .description(LAST_COMMITTED_POSITION.getDescription())
        .register(registry);
    Gauge.builder(LAST_WRITTEN_POSITION.getName(), lastWritten, Number::longValue)
        .description(LAST_WRITTEN_POSITION.getDescription())
        .register(registry);
  }

  public void increaseInflight() {
    inflightAppends.incrementAndGet();
  }

  public void decreaseInflight() {
    inflightAppends.decrementAndGet();
  }

  public void setInflightLimit(final long limit) {
    inflightLimit.set(limit);
  }

  public void increaseTriedAppends() {
    triedAppends.increment();
  }

  public void increaseDeferredAppends() {
    deferredAppends.increment();
  }

  public CloseableSilently startWriteTimer() {
    return MicrometerUtil.timer(appendLatency, Timer.start(registry));
  }

  public CloseableSilently startCommitTimer() {
    return MicrometerUtil.timer(commitLatency, Timer.start(registry));
  }

  public void setLastWrittenPosition(final long position) {
    lastWritten.set(position);
  }

  public void setLastCommittedPosition(final long position) {
    lastCommitted.set(position);
  }

  public void recordAppendedEntry(
      final int amount,
      final RecordType recordType,
      final ValueType valueType,
      final Intent intent) {
    recordAppended
        .computeIfAbsent(recordType, valueType, intent, this::registerRecordAppendedCounter)
        .increment(amount);
  }

  private Counter registerRecordAppendedCounter(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return Counter.builder(RECORD_APPENDED.getName())
        .description(RECORD_APPENDED.getDescription())
        .tag(RECORD_TYPE.asString(), recordType.name())
        .tag(VALUE_TYPE.asString(), valueType.name())
        .tag(INTENT.asString(), intent.name())
        .register(registry);
  }
}
