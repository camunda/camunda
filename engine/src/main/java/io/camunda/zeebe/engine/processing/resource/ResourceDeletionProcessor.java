/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class ResourceDeletionProcessor implements TypedRecordProcessor<ResourceDeletionRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final KeyGenerator keyGenerator;
  private final DecisionState decisionState;

  public ResourceDeletionProcessor(
      final Writers writers, final KeyGenerator keyGenerator, final DecisionState decisionState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.keyGenerator = keyGenerator;
    this.decisionState = decisionState;
  }

  @Override
  public void processRecord(final TypedRecord<ResourceDeletionRecord> command) {
    final var value = command.getValue();

    final var drgOptional = decisionState.findDecisionRequirementsByKey(value.getResourceKey());
    if (drgOptional.isPresent()) {
      deleteDecisionRequirements(drgOptional.get());
    }

    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ResourceDeletionIntent.DELETED, value);
    responseWriter.writeEventOnCommand(eventKey, ResourceDeletionIntent.DELETED, value, command);
  }

  private void deleteDecisionRequirements(final PersistedDecisionRequirements drg) {
    decisionState
        .findDecisionsByDecisionRequirementsKey(drg.getDecisionRequirementsKey())
        .forEach(this::deleteDecision);

    final var drgRecord =
        new DecisionRequirementsRecord()
            .setDecisionRequirementsId(BufferUtil.bufferAsString(drg.getDecisionRequirementsId()))
            .setDecisionRequirementsName(
                BufferUtil.bufferAsString(drg.getDecisionRequirementsName()))
            .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion())
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setResourceName(BufferUtil.bufferAsString(drg.getResourceName()))
            .setChecksum(drg.getChecksum())
            .setResource(drg.getResource());

    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), DecisionRequirementsIntent.DELETED, drgRecord);
  }

  private void deleteDecision(final PersistedDecision persistedDecision) {
    final var decisionRecord =
        new DecisionRecord()
            .setDecisionId(BufferUtil.bufferAsString(persistedDecision.getDecisionId()))
            .setDecisionName(BufferUtil.bufferAsString(persistedDecision.getDecisionName()))
            .setVersion(persistedDecision.getVersion())
            .setDecisionKey(persistedDecision.getDecisionKey())
            .setDecisionRequirementsId(
                BufferUtil.bufferAsString(persistedDecision.getDecisionRequirementsId()))
            .setDecisionRequirementsKey(persistedDecision.getDecisionRequirementsKey());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), DecisionIntent.DELETED, decisionRecord);
  }
}
