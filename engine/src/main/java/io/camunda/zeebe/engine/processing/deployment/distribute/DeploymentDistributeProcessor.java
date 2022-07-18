/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.deployment.DeploymentResponder;
import io.camunda.zeebe.engine.processing.deployment.MessageStartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;

public final class DeploymentDistributeProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final MessageStartEventSubscriptionManager messageStartEventSubscriptionManager;
  private final DeploymentResponder deploymentResponder;
  private final int partitionId;
  private final StateWriter stateWriter;

  public DeploymentDistributeProcessor(
      final ProcessState processState,
      final MessageStartEventSubscriptionState messageStartEventSubscriptionState,
      final DeploymentResponder deploymentResponder,
      final int partitionId,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    messageStartEventSubscriptionManager =
        new MessageStartEventSubscriptionManager(
            processState, messageStartEventSubscriptionState, keyGenerator);
    this.deploymentResponder = deploymentResponder;
    this.partitionId = partitionId;
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentRecord> event) {
    final var deploymentEvent = event.getValue();
    final var deploymentKey = event.getKey();

    stateWriter.appendFollowUpEvent(deploymentKey, DeploymentIntent.DISTRIBUTED, deploymentEvent);
    deploymentResponder.sendDeploymentResponse(deploymentKey, partitionId);

    messageStartEventSubscriptionManager.tryReOpenMessageStartEventSubscription(
        deploymentEvent, stateWriter);
  }
}
