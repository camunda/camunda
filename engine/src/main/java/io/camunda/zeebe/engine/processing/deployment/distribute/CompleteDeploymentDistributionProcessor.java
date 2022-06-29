/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.RejectionsBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;

public class CompleteDeploymentDistributionProcessor
    implements TypedRecordProcessor<DeploymentDistributionRecord> {

  private static final String REJECT_MSG_DEPLOYMENT_DISTRIBUTION_COMPLETED =
      "Expected to find pending deployment with key %d, but deployment distribution already completed.";

  private final DeploymentRecord emptyDeploymentRecord = new DeploymentRecord();
  private final StateBuilder stateBuilder;
  private final DeploymentState deploymentState;
  private final RejectionsBuilder rejectionWriter;

  public CompleteDeploymentDistributionProcessor(
      final DeploymentState deploymentState, final Writers writers) {
    stateBuilder = writers.state();
    this.deploymentState = deploymentState;
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentDistributionRecord> record) {

    final var deploymentKey = record.getKey();
    if (!deploymentState.hasPendingDeploymentDistribution(deploymentKey)) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(REJECT_MSG_DEPLOYMENT_DISTRIBUTION_COMPLETED, deploymentKey));
      return;
    }

    stateBuilder.appendFollowUpEvent(
        deploymentKey, DeploymentDistributionIntent.COMPLETED, record.getValue());

    if (!deploymentState.hasPendingDeploymentDistribution(deploymentKey)) {
      // to be consistent we write here as well an empty deployment record
      // https://github.com/zeebe-io/zeebe/issues/6314
      stateBuilder.appendFollowUpEvent(
          deploymentKey, DeploymentIntent.FULLY_DISTRIBUTED, emptyDeploymentRecord);
    }
  }
}
