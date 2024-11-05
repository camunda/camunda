/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.deployment.StartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public final class DeploymentDistributeProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final StartEventSubscriptionManager startEventSubscriptionManager;

  private final StateWriter stateWriter;
  private final DeploymentDistributionCommandSender deploymentDistributionCommandSender;

  public DeploymentDistributeProcessor(
      final ProcessingState processingState,
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    this.deploymentDistributionCommandSender = deploymentDistributionCommandSender;
    stateWriter = writers.state();
    startEventSubscriptionManager =
        new StartEventSubscriptionManager(processingState, keyGenerator, stateWriter);
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentRecord> event) {
    final var deploymentEvent = event.getValue();
    final var deploymentKey = event.getKey();

    stateWriter.appendFollowUpEvent(deploymentKey, DeploymentIntent.DISTRIBUTED, deploymentEvent);
    deploymentDistributionCommandSender.completeOnPartition(deploymentKey);

    startEventSubscriptionManager.tryReOpenStartEventSubscription(deploymentEvent);
  }
}
