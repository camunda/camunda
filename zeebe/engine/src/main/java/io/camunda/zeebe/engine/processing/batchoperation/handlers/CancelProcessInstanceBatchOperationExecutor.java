/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.handlers;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CancelProcessInstanceBatchOperationExecutor implements BatchOperationExecutor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(CancelProcessInstanceBatchOperationExecutor.class);

  final TypedCommandWriter commandWriter;

  public CancelProcessInstanceBatchOperationExecutor(final TypedCommandWriter commandWriter) {
    this.commandWriter = commandWriter;
  }

  @Override
  public void execute(final long itemKey, final PersistedBatchOperation batchOperation) {
    LOGGER.info("Cancelling process instance with key '{}'", itemKey);

    final var command = new ProcessInstanceRecord();
    command.setProcessInstanceKey(itemKey);
    commandWriter.appendFollowUpCommand(
        itemKey, ProcessInstanceIntent.CANCEL, command, batchOperation.getKey());
  }
}
