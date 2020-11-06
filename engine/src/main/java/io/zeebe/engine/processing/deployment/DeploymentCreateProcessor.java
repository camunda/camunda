/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.intent.DeploymentIntent;

public final class DeploymentCreateProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final WorkflowState workflowState;
  private final int partitionId;
  private final DeploymentResponder deploymentResponder;

  public DeploymentCreateProcessor(
      final WorkflowState workflowState,
      final DeploymentResponder deploymentResponder,
      final int partitionId) {
    this.workflowState = workflowState;
    this.deploymentResponder = deploymentResponder;
    this.partitionId = partitionId;
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final DeploymentRecord deploymentEvent = event.getValue();
    workflowState.putDeployment(deploymentEvent);
    streamWriter.appendFollowUpEvent(event.getKey(), DeploymentIntent.CREATED, deploymentEvent);
    deploymentResponder.sendDeploymentResponse(event.getKey(), partitionId);
  }
}
