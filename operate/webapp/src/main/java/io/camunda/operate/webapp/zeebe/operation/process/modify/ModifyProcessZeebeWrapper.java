/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static io.camunda.operate.webapp.zeebe.operation.adapter.ClientBasedAdapter.withOperationReference;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2;
import io.camunda.operate.util.ConditionalOnOperateCompatibility;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * This class is intended to provide an abstraction layer for modify process commands that are
 * created and sent to zeebe
 */
@Component
@ConditionalOnOperateCompatibility(enabled = "true")
public class ModifyProcessZeebeWrapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModifyProcessZeebeWrapper.class);

  private CamundaClient camundaClient;
  private final AddTokenHandler addTokenHandler;
  private final CancelTokenHandler cancelTokenHandler;
  private final MoveTokenHandler moveTokenHandler;

  public ModifyProcessZeebeWrapper(
      final CamundaClient camundaClient,
      final AddTokenHandler addTokenHandler,
      final CancelTokenHandler cancelTokenHandler,
      final MoveTokenHandler moveTokenHandler) {
    this.camundaClient = camundaClient;
    this.addTokenHandler = addTokenHandler;
    this.cancelTokenHandler = cancelTokenHandler;
    this.moveTokenHandler = moveTokenHandler;
  }

  public CamundaClient getCamundaClient() {
    return camundaClient;
  }

  public void setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public ModifyProcessInstanceCommandStep1 newModifyProcessInstanceCommand(
      final Long processInstanceKey) {
    return camundaClient.newModifyProcessInstanceCommand(processInstanceKey);
  }

  public ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2
      processTokenModifications(
          final Long processInstanceKey, final List<Modification> tokenModifications) {
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep = null;

    ModifyProcessInstanceCommandStep1 currentStep =
        newModifyProcessInstanceCommand(processInstanceKey);

    for (final var iter = tokenModifications.iterator(); iter.hasNext(); ) {
      final Modification modification = iter.next();
      ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep = null;
      switch (modification.getModification()) {
        case ADD_TOKEN:
          nextStep = addTokenHandler.addToken(currentStep, modification);
          break;
        case CANCEL_TOKEN:
          nextStep = cancelTokenHandler.cancelToken(currentStep, processInstanceKey, modification);
          break;
        case MOVE_TOKEN:
          nextStep = moveTokenHandler.moveToken(currentStep, processInstanceKey, modification);
          break;
        default:
          LOGGER.warn(
              "ModifyProcessInstanceHandler encountered a modification type that should have been filtered out: {}",
              modification.getModification());
          break;
      }

      // Append 'and' if there is at least one more operation to process
      if (nextStep != null) {
        lastStep = nextStep;
        if (iter.hasNext()) {
          currentStep = nextStep.and();
        }
      }
    }

    return lastStep;
  }

  public void sendModificationsToZeebe(
      final ModifyProcessInstanceCommandStep2 stepCommand, final String operationId) {
    if (stepCommand != null) {
      withOperationReference(stepCommand, operationId).send().join();
    }
  }
}
