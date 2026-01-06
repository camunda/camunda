/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.handlers;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteProcessInstanceBatchOperationExecutor implements BatchOperationExecutor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeleteProcessInstanceBatchOperationExecutor.class);

  private final TypedCommandWriter commandWriter;
  private final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  public DeleteProcessInstanceBatchOperationExecutor(
      final TypedCommandWriter commandWriter,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this.commandWriter = commandWriter;
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
  }

  @Override
  public void execute(final long itemKey, final PersistedBatchOperation batchOperation) {
    LOGGER.trace("Delete process instance with key '{}'", itemKey);

    final var authentication = batchOperation.getAuthentication();
    final var claims = brokerRequestAuthorizationConverter.convert(authentication);
    final var command = new HistoryDeletionRecord();
    command.setResourceKey(itemKey);
    command.setResourceType(HistoryDeletionType.PROCESS_INSTANCE);

    commandWriter.appendFollowUpCommand(
        itemKey,
        HistoryDeletionIntent.DELETE,
        command,
        FollowUpCommandMetadata.of(
            b -> b.batchOperationReference(batchOperation.getKey()).claims(claims)));
  }
}
