/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Set;

public class MetricsExporter implements Exporter {

  /**
   * Defines the time after instances or jobs are getting removed from the cache/map.
   *
   * <p>Using large TTL or increasing the current needs to be done with care. Right now we assume
   * that we can create at max ~150 PI per partition per second.
   *
   * <p>This means: 150 * TIME_TO_LIVE = max_cache_size max_cache_size * 4 (cache counts) * 8 (long)
   * * 8 (long) = estimated_max_used_spaced_per_partition (roughly)
   * estimated_max_used_spaced_per_partition * partitions = max_used_spaced_per_broker When using a
   * TTL of 60 seconds this means: max_cache_size = 9000 (entries)
   * estimated_max_used_spaced_per_partition = 2.304.000 (bytes) = 2.3 MB
   *
   * <p>The estimated_max_used_spaced_per_partition is slightly a little more, since the TreeMap
   * might consume more memory than the Long2LongHashMap.
   */
  public static final Duration TIME_TO_LIVE = Duration.ofSeconds(60);

  private ExecutionLatencyMetrics executionLatencyMetrics;
  private final TtlKeyCache processInstanceCache;
  private final TtlKeyCache jobCache;
  private InstantSource clock;

  private Controller controller;

  public MetricsExporter() {
    this(new TtlKeyCache(TIME_TO_LIVE.toMillis()), new TtlKeyCache(TIME_TO_LIVE.toMillis()));
  }

  @VisibleForTesting
  MetricsExporter(final TtlKeyCache processInstanceCache, final TtlKeyCache jobCache) {
    this.processInstanceCache = processInstanceCache;
    this.jobCache = jobCache;
  }

  @Override
  public void configure(final Context context) throws Exception {
    final MeterRegistry meterRegistry = context.getMeterRegistry();
    executionLatencyMetrics = new ExecutionLatencyMetrics(meterRegistry);
    clock = context.clock();
    context.setFilter(
        new RecordFilter() {
          private static final Set<ValueType> ACCEPTED_VALUE_TYPES =
              Set.of(ValueType.JOB, ValueType.JOB_BATCH, ValueType.PROCESS_INSTANCE);

          @Override
          public boolean acceptType(final RecordType recordType) {
            return recordType == RecordType.EVENT;
          }

          @Override
          public boolean acceptValue(final ValueType valueType) {
            return ACCEPTED_VALUE_TYPES.contains(valueType);
          }
        });
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;

    controller.scheduleCancellableTask(TIME_TO_LIVE, this::cleanUp);
  }

  @Override
  public void close() {
    processInstanceCache.clear();
    jobCache.clear();
  }

  @Override
  public void export(final Record<?> record) {
    final var recordKey = record.getKey();

    final var currentValueType = record.getValueType();
    if (currentValueType == ValueType.JOB) {
      handleJobRecord(record, recordKey);
    } else if (currentValueType == ValueType.JOB_BATCH) {
      handleJobBatchRecord(record);
    } else if (currentValueType == ValueType.PROCESS_INSTANCE) {
      handleProcessInstanceRecord(record, recordKey);
    }

    controller.updateLastExportedRecordPosition(record.getPosition());
  }

  private void handleProcessInstanceRecord(final Record<?> record, final long recordKey) {
    final var currentIntent = record.getIntent();

    if (currentIntent == ProcessInstanceIntent.ELEMENT_ACTIVATING
        && isProcessInstanceRecord(record)) {
      processInstanceCache.store(recordKey, record.getTimestamp());
    } else if (currentIntent == ProcessInstanceIntent.ELEMENT_COMPLETED
        && isProcessInstanceRecord(record)) {
      final var creationTime = processInstanceCache.remove(recordKey);
      executionLatencyMetrics.observeProcessInstanceExecutionTime(
          creationTime, record.getTimestamp());
    }
    executionLatencyMetrics.setCurrentProcessInstanceCount(processInstanceCache.size());
  }

  private void handleJobRecord(final Record<?> record, final long recordKey) {
    final var currentIntent = record.getIntent();

    if (currentIntent == JobIntent.CREATED) {
      jobCache.store(recordKey, record.getTimestamp());
    } else if (currentIntent == JobIntent.COMPLETED) {
      final var creationTime = jobCache.remove(recordKey);
      executionLatencyMetrics.observeJobLifeTime(creationTime, record.getTimestamp());
    }
    executionLatencyMetrics.setCurrentJobsCount(jobCache.size());
  }

  private void handleJobBatchRecord(final Record<?> record) {
    final var currentIntent = record.getIntent();

    if (currentIntent == JobBatchIntent.ACTIVATED) {
      final var value = (JobBatchRecordValue) record.getValue();
      for (final long jobKey : value.getJobKeys()) {
        final var creationTime = jobCache.get(jobKey);
        executionLatencyMetrics.observeJobActivationTime(creationTime, record.getTimestamp());
      }
    }
  }

  private void cleanUp() {
    final var deadTime = clock.millis() - TIME_TO_LIVE.toMillis();
    processInstanceCache.cleanup(deadTime);
    jobCache.cleanup(deadTime);
    controller.scheduleCancellableTask(TIME_TO_LIVE, this::cleanUp);
  }

  public static ExporterCfg defaultConfig() {
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(MetricsExporter.class.getName());
    return exporterCfg;
  }

  public static String defaultExporterId() {
    return MetricsExporter.class.getSimpleName();
  }

  private static boolean isProcessInstanceRecord(final Record<?> record) {
    final var recordValue = (ProcessInstanceRecordValue) record.getValue();
    return BpmnElementType.PROCESS == recordValue.getBpmnElementType();
  }
}
