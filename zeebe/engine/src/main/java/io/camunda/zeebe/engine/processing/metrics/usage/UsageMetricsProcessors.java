/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics.usage;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;

public class UsageMetricsProcessors {

  public static void addUsageMetricsProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final EngineConfiguration config,
      final InstantSource clock,
      final MutableProcessingState processingState,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    typedRecordProcessors
        .onCommand(
            ValueType.USAGE_METRIC,
            UsageMetricIntent.EXPORT,
            new UsageMetricExportProcessor(
                processingState.getUsageMetricState(), writers, keyGenerator, clock))
        .withListener(
            new UsageMetricsCheckScheduler(config.getUsageMetricsExportInterval(), clock));
  }
}
