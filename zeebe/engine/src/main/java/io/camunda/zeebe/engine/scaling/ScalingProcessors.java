/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.scaling.redistribution.RedistributionBehavior;
import io.camunda.zeebe.engine.scaling.redistribution.RedistributionCompleteProcessor;
import io.camunda.zeebe.engine.scaling.redistribution.RedistributionContinueProcessor;
import io.camunda.zeebe.engine.scaling.redistribution.RedistributionStartProcessor;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.scaling.RedistributionIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/** Utility class to add all scaling related processors to the {@link TypedRecordProcessors}. */
public final class ScalingProcessors {
  private ScalingProcessors() {}

  public static void addScalingProcessors(
      final RedistributionBehavior redistributionBehavior,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState) {
    typedRecordProcessors.onCommand(
        ValueType.SCALE,
        ScaleIntent.SCALE_UP,
        new ScaleUpProcessor(keyGenerator, writers, processingState));
    typedRecordProcessors.onCommand(
        ValueType.REDISTRIBUTION,
        RedistributionIntent.START,
        new RedistributionStartProcessor(redistributionBehavior));
    typedRecordProcessors.onCommand(
        ValueType.REDISTRIBUTION,
        RedistributionIntent.CONTINUE,
        new RedistributionContinueProcessor(redistributionBehavior));
    typedRecordProcessors.onCommand(
        ValueType.REDISTRIBUTION,
        RedistributionIntent.COMPLETE,
        new RedistributionCompleteProcessor(writers));
  }
}
