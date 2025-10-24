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
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import java.util.ArrayDeque;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModifyProcessInstanceBatchOperationExecutor implements BatchOperationExecutor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ModifyProcessInstanceBatchOperationExecutor.class);

  final TypedCommandWriter commandWriter;
  private final ElementInstanceState elementInstanceState;
  private final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  public ModifyProcessInstanceBatchOperationExecutor(
      final TypedCommandWriter commandWriter,
      final ElementInstanceState elementInstanceState,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this.commandWriter = commandWriter;
    this.elementInstanceState = elementInstanceState;
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
  }

  @Override
  public void execute(final long processInstanceKey, final PersistedBatchOperation batchOperation) {

    final var moveInstructions =
        batchOperation.getModificationPlan().getMoveInstructions().stream()
            .collect(
                Collectors.toMap(
                    ProcessInstanceModificationMoveInstructionValue::getSourceElementId,
                    ProcessInstanceModificationMoveInstructionValue::getTargetElementId));

    final var authentication = batchOperation.getAuthentication();
    final var claims = brokerRequestAuthorizationConverter.convert(authentication);
    final var command = new ProcessInstanceModificationRecord();
    command.setProcessInstanceKey(processInstanceKey);

    // avoid stackoverflow using a queue to iterate over the descendants instead of recursion
    final var elementInstances =
        new ArrayDeque<>(elementInstanceState.getChildren(processInstanceKey));
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();

      if (moveInstructions.containsKey(elementInstance.getValue().getElementId())) {
        final var activateInstruction =
            new ProcessInstanceModificationActivateInstruction()
                .setElementId(moveInstructions.get(elementInstance.getValue().getElementId()))
                .setAncestorScopeKey(elementInstance.getParentKey());
        final var terminateInstruction =
            new ProcessInstanceModificationTerminateInstruction()
                .setElementInstanceKey(elementInstance.getKey());

        command.addActivateInstruction(activateInstruction);
        command.addTerminateInstruction(terminateInstruction);
      }

      elementInstances.addAll(elementInstanceState.getChildren(elementInstance.getKey()));
    }

    LOGGER.trace("Modifying process instance with key '{}'", processInstanceKey);
    commandWriter.appendFollowUpCommand(
        processInstanceKey,
        ProcessInstanceModificationIntent.MODIFY,
        command,
        FollowUpCommandMetadata.of(
            b -> b.batchOperationReference(batchOperation.getKey()).claims(claims)));
  }
}
