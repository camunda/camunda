/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.concurrent.UnsafeBuffer;

final class DecisionRequirementsDeletionBehavior {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final DecisionState decisionState;

  DecisionRequirementsDeletionBehavior(
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final KeyGenerator keyGenerator,
      final DecisionState decisionState) {
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.keyGenerator = keyGenerator;
    this.decisionState = decisionState;
  }

  void deleteDecisionRequirements(
      final DeployedDrg drg,
      final TypedRecord<ResourceDeletionRecord> command,
      final long eventKey) {
    decisionState
        .findDecisionsByTenantAndDecisionRequirementsKey(
            drg.getTenantId(), drg.getDecisionRequirementsKey())
        .forEach(this::deleteDecision);

    if (!command.isCommandDistributed() && command.getValue().isDeleteHistory()) {
      deleteDecisionInstanceHistory(drg.getDecisionRequirementsKey(), eventKey, command.getValue());
    }

    final var drgRecord =
        new DecisionRequirementsRecord()
            .setDecisionRequirementsId(bufferAsString(drg.getDecisionRequirementsId()))
            .setDecisionRequirementsName(bufferAsString(drg.getDecisionRequirementsName()))
            .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion())
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setResourceName(bufferAsString(drg.getResourceName()))
            .setChecksum(drg.getChecksum())
            .setResource(drg.getResource())
            .setTenantId(drg.getTenantId())
            .setDeploymentKey(drg.getDeploymentKey());

    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), DecisionRequirementsIntent.DELETED, drgRecord);
  }

  void deleteDecisionInstanceHistory(
      final long decisionRequirementsKey,
      final long eventKey,
      final ResourceDeletionRecord resourceDeletionRecord) {
    final var filter =
        new DecisionInstanceFilter.Builder()
            .decisionRequirementsKeys(decisionRequirementsKey)
            .build();
    final long batchOperationKey = keyGenerator.nextKey();
    final var batchOperationRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)))
            .setAuthentication(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(CamundaAuthentication.anonymous())))
            .setFollowUpCommand(
                ValueType.HISTORY_DELETION,
                HistoryDeletionIntent.DELETE,
                new HistoryDeletionRecord()
                    .setResourceKey(decisionRequirementsKey)
                    .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS));
    commandWriter.appendFollowUpCommand(
        eventKey, BatchOperationIntent.CREATE, batchOperationRecord);

    resourceDeletionRecord.setBatchOperationKey(batchOperationKey);
    resourceDeletionRecord.setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE);
  }

  private void deleteDecision(final PersistedDecision persistedDecision) {
    final var decisionRecord =
        new DecisionRecord()
            .setDecisionId(bufferAsString(persistedDecision.getDecisionId()))
            .setDecisionName(bufferAsString(persistedDecision.getDecisionName()))
            .setVersion(persistedDecision.getVersion())
            .setVersionTag(persistedDecision.getVersionTag())
            .setDecisionKey(persistedDecision.getDecisionKey())
            .setDecisionRequirementsId(
                bufferAsString(persistedDecision.getDecisionRequirementsId()))
            .setDecisionRequirementsKey(persistedDecision.getDecisionRequirementsKey())
            .setTenantId(persistedDecision.getTenantId())
            .setDeploymentKey(persistedDecision.getDeploymentKey());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), DecisionIntent.DELETED, decisionRecord);
  }
}
