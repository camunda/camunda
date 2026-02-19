/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl;

import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.COMMIT_LATENCY;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.EXPORTING_RATE;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.FLOW_CONTROL_OUTCOME;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.INFLIGHT_APPENDS;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.INFLIGHT_REQUESTS;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.LAST_COMMITTED_POSITION;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.LAST_WRITTEN_POSITION;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.PARTITION_LOAD;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.RECORD_APPENDED;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.REQUEST_LIMIT;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.TOTAL_APPEND_TRY_COUNT;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.TOTAL_DEFERRED_APPEND_COUNT;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.TOTAL_DROPPED_REQUESTS;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.TOTAL_RECEIVED_REQUESTS;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.WRITE_LATENCY;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.WRITE_RATE_LIMIT;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.WRITE_RATE_MAX_LIMIT;

import io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.FlowControlContext;
import io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.FlowControlKeyNames;
import io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.FlowControlOutcome;
import io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.RecordAppendedKeyNames;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.log.WriteContext.InterPartition;
import io.camunda.zeebe.logstreams.log.WriteContext.Internal;
import io.camunda.zeebe.logstreams.log.WriteContext.ProcessingResult;
import io.camunda.zeebe.logstreams.log.WriteContext.Scheduled;
import io.camunda.zeebe.logstreams.log.WriteContext.UserCommand;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.collection.Map3D;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;

public final class LogStreamMetrics {
  private final AtomicLong inflightAppends = new AtomicLong();
  private final AtomicLong inflightRequests = new AtomicLong();
  private final AtomicLong requestLimit = new AtomicLong();
  private final AtomicLong lastCommitted = new AtomicLong();
  private final AtomicLong lastWritten = new AtomicLong();
  private final AtomicLong exportingRate = new AtomicLong();
  private final AtomicLong writeRateMaxLimit = new AtomicLong();
  private final AtomicLong writeRateLimit = new AtomicLong();
  private final AtomicLong partitionLoad = new AtomicLong();
  private final Map3D<RecordType, ValueType, Intent, Counter> recordAppended = Map3D.simple();
  private final Table<FlowControlContext, FlowControlOutcome, Counter> flowControlOutcome =
      Table.ofEnum(FlowControlContext.class, FlowControlOutcome.class, Counter[]::new);

  private final MeterRegistry registry;
  private final Counter deferredAppends;
  private final Counter triedAppends;
  private final Counter receivedRequests;
  private final Counter droppedRequests;
  private final Timer commitLatency;
  private final Timer appendLatency;

  public LogStreamMetrics(final MeterRegistry registry) {
    this.registry = registry;
    deferredAppends = registerCounter(TOTAL_DEFERRED_APPEND_COUNT);
    triedAppends = registerCounter(TOTAL_APPEND_TRY_COUNT);
    receivedRequests = registerCounter(TOTAL_RECEIVED_REQUESTS);
    droppedRequests = registerCounter(TOTAL_DROPPED_REQUESTS);
    commitLatency = MicrometerUtil.buildTimer(COMMIT_LATENCY).register(registry);
    appendLatency = MicrometerUtil.buildTimer(WRITE_LATENCY).register(registry);

    registerGauge(INFLIGHT_APPENDS, inflightAppends);
    registerGauge(INFLIGHT_REQUESTS, inflightAppends);
    registerGauge(REQUEST_LIMIT, requestLimit);
    registerGauge(LAST_COMMITTED_POSITION, lastCommitted);
    registerGauge(LAST_WRITTEN_POSITION, lastWritten);
    registerGauge(EXPORTING_RATE, exportingRate);
    registerGauge(WRITE_RATE_MAX_LIMIT, writeRateMaxLimit);

    Gauge.builder(WRITE_RATE_LIMIT.getName(), writeRateLimit, LogStreamMetrics::longToDouble)
        .description(WRITE_RATE_LIMIT.getDescription())
        .register(registry);
    Gauge.builder(PARTITION_LOAD.getName(), partitionLoad, LogStreamMetrics::longToDouble)
        .description(PARTITION_LOAD.getDescription())
        .register(registry);
  }

  public void increaseInflightAppends() {
    inflightAppends.incrementAndGet();
  }

  public void decreaseInflightAppends() {
    inflightAppends.decrementAndGet();
  }

  public void setInflightRequests(final int count) {
    inflightRequests.set(count);
  }

  public void setRequestLimit(final int limit) {
    requestLimit.set(limit);
  }

  public void increaseInflightRequests() {
    inflightRequests.incrementAndGet();
  }

  public void decreaseInflightRequests() {
    inflightRequests.decrementAndGet();
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

  public void flowControlAccepted(final WriteContext context, final int size) {
    triedAppends.increment();

    if (context instanceof UserCommand) {
      receivedRequests.increment();
    }

    flowControlOutcome
        .computeIfAbsent(
            tagForContext(context),
            FlowControlOutcome.ACCEPTED,
            this::registerFlowControlOutcomeCounter)
        .increment(size);
  }

  public void flowControlRejected(
      final WriteContext context, final int size, final Rejection reason) {
    triedAppends.increment();
    deferredAppends.increment();

    if (context instanceof UserCommand) {
      receivedRequests.increment();
      droppedRequests.increment();
    }

    flowControlOutcome
        .computeIfAbsent(
            tagForContext(context),
            tagForRejection(reason),
            this::registerFlowControlOutcomeCounter)
        .increment(size);
  }

  public void setExportingRate(final long value) {
    exportingRate.set(value);
  }

  public void setWriteRateMaxLimit(final long value) {
    writeRateMaxLimit.set(value);
  }

  public void setPartitionLoad(final double load) {
    partitionLoad.set(Double.doubleToLongBits(load));
  }

  public void setWriteRateLimit(final double value) {
    writeRateLimit.set(Double.doubleToLongBits(value));
  }

  private Counter registerRecordAppendedCounter(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    return Counter.builder(RECORD_APPENDED.getName())
        .description(RECORD_APPENDED.getDescription())
        .tag(RecordAppendedKeyNames.RECORD_TYPE.asString(), recordType.name())
        .tag(RecordAppendedKeyNames.VALUE_TYPE.asString(), valueType.name())
        .tag(RecordAppendedKeyNames.INTENT.asString(), intent.name())
        .register(registry);
  }

  private Counter registerFlowControlOutcomeCounter(
      final FlowControlContext flowControlContext, final FlowControlOutcome flowControlOutcome) {
    return Counter.builder(FLOW_CONTROL_OUTCOME.getName())
        .description(FLOW_CONTROL_OUTCOME.getDescription())
        .tag(FlowControlKeyNames.CONTEXT.asString(), flowControlContext.getValue())
        .tag(FlowControlKeyNames.OUTCOME.asString(), flowControlOutcome.getValue())
        .register(registry);
  }

  private void registerGauge(final ExtendedMeterDocumentation doc, final AtomicLong gauge) {
    Gauge.builder(doc.getName(), gauge, AtomicLong::get)
        .description(doc.getDescription())
        .register(registry);
  }

  private Counter registerCounter(final ExtendedMeterDocumentation doc) {
    return Counter.builder(doc.getName()).description(doc.getDescription()).register(registry);
  }

  private static double longToDouble(final AtomicLong value) {
    return Double.longBitsToDouble(value.get());
  }

  private static FlowControlOutcome tagForRejection(final Rejection reason) {
    return switch (reason) {
      case WriteRateLimitExhausted -> FlowControlOutcome.WRITE_RATE_LIMIT_EXHAUSTED;
      case RequestLimitExhausted -> FlowControlOutcome.REQUEST_LIMIT_EXHAUSTED;
    };
  }

  private static FlowControlContext tagForContext(final WriteContext context) {
    return switch (context) {
      case final UserCommand ignored -> FlowControlContext.USER_COMMAND;
      case final ProcessingResult ignored -> FlowControlContext.PROCESSING_RESULT;
      case final InterPartition ignored -> FlowControlContext.INTER_PARTITION;
      case final Scheduled ignored -> FlowControlContext.SCHEDULED;
      case final Internal ignored -> FlowControlContext.INTERNAL;
    };
  }
}
