/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.historydeletion;

import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Creates the batch operation that deletes a deleted resource's history: one item per instance,
 * then the resource itself.
 *
 * <p>Uses anonymous authentication — the caller was already authorized to delete the resource, so
 * this is an internal consequence. Create it only on the partition that received the original
 * command; it distributes itself to the others.
 */
public final class HistoryDeletionBehavior {

  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;

  public HistoryDeletionBehavior(
      final KeyGenerator keyGenerator, final TypedCommandWriter commandWriter) {
    this.keyGenerator = keyGenerator;
    this.commandWriter = commandWriter;
  }

  /**
   * Deletes the history of every instance of the given process definition, and of the definition
   * itself.
   *
   * @return the key of the created batch operation
   */
  public long deleteProcessInstanceHistory(final long processDefinitionKey) {
    final var filter =
        new ProcessInstanceFilter.Builder().processDefinitionKeys(processDefinitionKey).build();
    return createBatchOperation(
        BatchOperationType.DELETE_PROCESS_INSTANCE,
        MsgPackConverter.convertToMsgPack(filter),
        new HistoryDeletionRecord()
            .setResourceKey(processDefinitionKey)
            .setResourceType(HistoryDeletionType.PROCESS_DEFINITION));
  }

  /**
   * Deletes the history of every instance of the decisions belonging to the given decision
   * requirements, and of the decision requirements themselves.
   *
   * @return the key of the created batch operation
   */
  public long deleteDecisionInstanceHistory(final long decisionRequirementsKey) {
    final var filter =
        new DecisionInstanceFilter.Builder()
            .decisionRequirementsKeys(decisionRequirementsKey)
            .build();
    return createBatchOperation(
        BatchOperationType.DELETE_DECISION_INSTANCE,
        MsgPackConverter.convertToMsgPack(filter),
        new HistoryDeletionRecord()
            .setResourceKey(decisionRequirementsKey)
            .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS));
  }

  private long createBatchOperation(
      final BatchOperationType batchOperationType,
      final byte[] entityFilter,
      final HistoryDeletionRecord followUpCommand) {
    final long batchOperationKey = keyGenerator.nextKey();
    final var batchOperationRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(batchOperationType)
            .setEntityFilter(new UnsafeBuffer(entityFilter))
            .setAuthentication(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(CamundaAuthentication.anonymous())))
            .setFollowUpCommand(
                ValueType.HISTORY_DELETION, HistoryDeletionIntent.DELETE, followUpCommand);
    commandWriter.appendFollowUpCommand(
        batchOperationKey, BatchOperationIntent.CREATE, batchOperationRecord);
    return batchOperationKey;
  }
}
