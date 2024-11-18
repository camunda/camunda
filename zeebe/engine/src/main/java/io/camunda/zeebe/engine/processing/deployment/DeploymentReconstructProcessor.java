/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public class DeploymentReconstructProcessor implements TypedRecordProcessor<DeploymentRecord> {
  private final KeyGenerator keyGenerator;
  private final ProcessState processState;
  private final FormState formState;
  private final DecisionState decisionState;
  private final StateWriter stateWriter;

  public DeploymentReconstructProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final Writers writers) {
    this.keyGenerator = keyGenerator;
    processState = processingState.getProcessState();
    formState = processingState.getFormState();
    decisionState = processingState.getDecisionState();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentRecord> record) {
    final var key = keyGenerator.nextKey();
    // TODO: Only append the event when there are no more deployments to reconstruct
    stateWriter.appendFollowUpEvent(key, DeploymentIntent.RECONSTRUCTED_ALL, record.getValue());
  }
}
