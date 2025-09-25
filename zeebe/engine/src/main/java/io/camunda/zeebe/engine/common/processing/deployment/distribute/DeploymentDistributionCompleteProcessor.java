/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.distribute;

import io.camunda.zeebe.engine.common.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.common.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.immutable.DeploymentState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public class DeploymentDistributionCompleteProcessor
    implements TypedRecordProcessor<DeploymentDistributionRecord> {

  private static final String REJECT_MSG_DEPLOYMENT_DISTRIBUTION_COMPLETED =
      "Expected to find pending deployment with key %d, but deployment distribution already completed.";

  private final DeploymentRecord emptyDeploymentRecord = new DeploymentRecord();
  private final StateWriter stateWriter;
  private final DeploymentState deploymentState;
  private final TypedRejectionWriter rejectionWriter;

  public DeploymentDistributionCompleteProcessor(
      final DeploymentState deploymentState, final Writers writers) {
    stateWriter = writers.state();
    this.deploymentState = deploymentState;
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentDistributionRecord> record) {

    final var deploymentKey = record.getKey();
    final var partitionId = record.getValue().getPartitionId();
    if (!deploymentState.hasPendingDeploymentDistribution(deploymentKey, partitionId)) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(REJECT_MSG_DEPLOYMENT_DISTRIBUTION_COMPLETED, deploymentKey));
      return;
    }

    stateWriter.appendFollowUpEvent(
        deploymentKey, DeploymentDistributionIntent.COMPLETED, record.getValue());

    if (!deploymentState.hasPendingDeploymentDistribution(deploymentKey)) {
      // to be consistent we write here as well an empty deployment record
      // https://github.com/zeebe-io/zeebe/issues/6314
      stateWriter.appendFollowUpEvent(
          deploymentKey, DeploymentIntent.FULLY_DISTRIBUTED, emptyDeploymentRecord);
    }
  }
}
