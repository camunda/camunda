/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static io.zeebe.protocol.record.intent.DeploymentIntent.CREATE;

import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.protocol.record.ValueType;

public final class DeploymentEventProcessors {

  public static void addDeploymentCreateProcessor(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableWorkflowState workflowState,
      final DeploymentResponder deploymentResponder,
      final int partitionId) {
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT,
        CREATE,
        new DeploymentCreateProcessor(workflowState, deploymentResponder, partitionId));
  }

  public static void addTransformingDeploymentProcessor(
      final TypedRecordProcessors typedRecordProcessors,
      final ZeebeState zeebeState,
      final CatchEventBehavior catchEventBehavior,
      final ExpressionProcessor expressionProcessor,
      final int partitionsCount,
      final Writers writers) {
    final var processor =
        new TransformingDeploymentCreateProcessor(
            zeebeState, catchEventBehavior, expressionProcessor, partitionsCount, writers);
    typedRecordProcessors.onCommand(ValueType.DEPLOYMENT, CREATE, processor);
  }
}
