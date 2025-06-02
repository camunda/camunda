/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import java.time.InstantSource;

public class UsageMetricsProcessors {

  public static void addUsageMetricsProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final EngineConfiguration config,
      final InstantSource clock) {
    typedRecordProcessors.withListener(new UsageMetricsCheckerScheduler(config, clock));
  }
}
