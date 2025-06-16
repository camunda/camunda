/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scaling;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/** Utility class to add all scaling related processors to the {@link TypedRecordProcessors}. */
public final class ScalingProcessors {
  private ScalingProcessors() {}

  public static void addScalingProcessors(
      final CommandDistributionBehavior distributionBehavior,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState) {
    typedRecordProcessors.onCommand(
        ValueType.SCALE,
        ScaleIntent.SCALE_UP,
        new ScaleUpProcessor(keyGenerator, writers, processingState, distributionBehavior));
    typedRecordProcessors.onCommand(
        ValueType.SCALE,
        ScaleIntent.STATUS,
        new ScaleUpStatusProcessor(keyGenerator, writers, processingState.getRoutingState()));
    typedRecordProcessors.onCommand(
        ValueType.SCALE,
        ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
        new MarkPartitionBootstrappedProcessor(
            keyGenerator, writers, processingState, distributionBehavior));
  }
}
