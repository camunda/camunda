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
import io.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import java.util.function.Consumer;

public class CompleteDeploymentDistributionProcessor
    implements TypedRecordProcessor<DeploymentDistributionRecord> {

  private final StateWriter stateWriter;

  public CompleteDeploymentDistributionProcessor(final Writers writers) {
    stateWriter = writers.state();
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

    // todo(zell): https://github.com/zeebe-io/zeebe/issues/6173
    // check whether it was the last pending
    // then write Deployment.FULLY_DISTRIBUTED
  }
}
