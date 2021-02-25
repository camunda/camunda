/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.distribute;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.immutable.DeploymentState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import java.util.function.Consumer;

public class CompleteDeploymentDistributionProcessor
    implements TypedRecordProcessor<DeploymentDistributionRecord> {

  private final DeploymentRecord emptyDeploymentRecord = new DeploymentRecord();
  private final StateWriter stateWriter;
  private final DeploymentState deploymentState;

  public CompleteDeploymentDistributionProcessor(
      final DeploymentState deploymentState, final Writers writers) {
    stateWriter = writers.state();
    this.deploymentState = deploymentState;
  }

  @Override
  public void processRecord(
      final long position,
      final TypedRecord<DeploymentDistributionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final var deploymentKey = record.getKey();
    // DeploymentDistribution.COMPLETE is idempotent
    // even if we already removed the pending we will not reject it
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
