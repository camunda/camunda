/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PolicyUpdateProcessors {

  private PolicyUpdateProcessors() {}

  public static void addPolicyUpdateProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final Writers writers) {
    typedRecordProcessors.onCommand(
        ValueType.POLICY_UPDATE,
        PolicyUpdateIntent.APPEND_CHUNK,
        new PolicyUpdateAppendChunkProcessor(processingState.getPolicyUpdateState(), writers));
    typedRecordProcessors.onCommand(
        ValueType.POLICY_UPDATE,
        PolicyUpdateIntent.APPLY,
        new PolicyUpdateApplyProcessor(
            processingState.getPolicyUpdateState(), processingState, writers));
  }
}
