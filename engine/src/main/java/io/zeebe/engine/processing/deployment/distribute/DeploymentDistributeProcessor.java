/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.distribute;

import io.zeebe.engine.processing.deployment.MessageStartEventSubscriptionManager;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.DeploymentState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.util.sched.ActorControl;
import java.util.function.Consumer;

public final class DeploymentDistributeProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final DeploymentState deploymentState;
  private final DeploymentDistributor deploymentDistributor;
  private final ActorControl actor;
  private final MessageStartEventSubscriptionManager messageStartEventSubscriptionManager;

  public DeploymentDistributeProcessor(
      final ActorControl actor,
      final ZeebeState zeebeState,
      final DeploymentDistributor deploymentDistributor) {
    deploymentState = zeebeState.getDeploymentState();
    messageStartEventSubscriptionManager =
        new MessageStartEventSubscriptionManager(zeebeState.getWorkflowState());
    this.deploymentDistributor = deploymentDistributor;
    this.actor = actor;
  }

  @Override
  public void processRecord(
      final long position,
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final var deploymentRecord = event.getValue();

    messageStartEventSubscriptionManager.tryReOpenMessageStartEventSubscription(
        deploymentRecord, streamWriter);
  }
}
