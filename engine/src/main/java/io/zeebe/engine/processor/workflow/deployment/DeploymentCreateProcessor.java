/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.DeploymentResponder;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.DeploymentIntent;

public class DeploymentCreateProcessor implements TypedRecordProcessor<DeploymentRecord> {
  public static final String DEPLOYMENT_ALREADY_EXISTS_MESSAGE =
      "Expected to create a new deployment with key '%d', but there is already an existing deployment with that key";

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
    if (workflowState.putDeployment(event.getKey(), deploymentEvent)) {
      streamWriter.appendFollowUpEvent(event.getKey(), DeploymentIntent.CREATED, deploymentEvent);
    } else {
      streamWriter.appendRejection(
          event,
          RejectionType.ALREADY_EXISTS,
          String.format(DEPLOYMENT_ALREADY_EXISTS_MESSAGE, event.getKey()));
    }

    deploymentResponder.sendDeploymentResponse(event.getKey(), partitionId);
  }
}
