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
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrateProcessInstanceBatchOperationExecutor implements BatchOperationExecutor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MigrateProcessInstanceBatchOperationExecutor.class);

  final TypedCommandWriter commandWriter;
  final BatchOperationState batchOperationState;
  private final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  public MigrateProcessInstanceBatchOperationExecutor(
      final TypedCommandWriter commandWriter,
      final BatchOperationState batchOperationState,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this.commandWriter = commandWriter;
    this.batchOperationState = batchOperationState;
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
  }

  @Override
  public void execute(final long processInstanceKey, final PersistedBatchOperation batchOperation) {
    LOGGER.trace("Migrate process instance with key '{}'", processInstanceKey);

    final var migrationPlan = batchOperation.getMigrationPlan();

    final var authentication = batchOperation.getAuthentication();
    final var claims = brokerRequestAuthorizationConverter.convert(authentication);
    final var command = new ProcessInstanceMigrationRecord();
    command.setProcessInstanceKey(processInstanceKey);
    command.setTargetProcessDefinitionKey(migrationPlan.getTargetProcessDefinitionKey());
    migrationPlan
        .getMappingInstructions()
        .forEach(
            mappingInstruction -> {
              command.addMappingInstruction(
                  new ProcessInstanceMigrationMappingInstruction()
                      .setSourceElementId(mappingInstruction.getSourceElementId())
                      .setTargetElementId(mappingInstruction.getTargetElementId()));
            });

    commandWriter.appendFollowUpCommand(
        processInstanceKey,
        ProcessInstanceMigrationIntent.MIGRATE,
        command,
        FollowUpCommandMetadata.of(
            b -> b.batchOperationReference(batchOperation.getKey()).claims(claims)));
  }
}
