/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.jobmetrics;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobMetricsState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;

/** Registers processors for job metrics batch records. */
public final class JobMetricsProcessors {

  private JobMetricsProcessors() {
    // utility class
  }

  public static void addJobMetricsProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final EngineConfiguration config,
      final JobMetricsState jobMetricsState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final InstantSource clock) {
    typedRecordProcessors
        .onCommand(
            ValueType.JOB_METRICS_BATCH,
            JobMetricsBatchIntent.EXPORT,
            new JobMetricsBatchExportProcessor(jobMetricsState, writers, keyGenerator, clock))
        .withListener(new JobMetricsCheckerScheduler(config, clock));
  }
}
