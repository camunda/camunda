/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment;

import static io.zeebe.protocol.record.intent.DeploymentIntent.CREATE;

import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.DeploymentResponder;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.protocol.record.ValueType;

public class DeploymentEventProcessors {

  public static void addDeploymentCreateProcessor(
      TypedRecordProcessors typedRecordProcessors,
      WorkflowState workflowState,
      DeploymentResponder deploymentResponder,
      final int partitionId) {
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT,
        CREATE,
        new DeploymentCreateProcessor(workflowState, deploymentResponder, partitionId));
  }

  public static void addTransformingDeploymentProcessor(
      TypedRecordProcessors typedRecordProcessors,
      ZeebeState zeebeState,
      CatchEventBehavior catchEventBehavior) {
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT,
        CREATE,
        new TransformingDeploymentCreateProcessor(zeebeState, catchEventBehavior));
  }
}
