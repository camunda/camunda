/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CancelTokenHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(CancelTokenHandler.class);
  private final FlowNodeInstanceReader flowNodeInstanceReader;

  public CancelTokenHandler(final FlowNodeInstanceReader flowNodeInstanceReader) {
    this.flowNodeInstanceReader = flowNodeInstanceReader;
  }

  public ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 cancelToken(
      final ModifyProcessInstanceCommandStep1 currentStep,
      final Long processInstanceKey,
      final Modification modification) {
    final String flowNodeInstanceKey = modification.getFromFlowNodeInstanceKey();

    // Build the list of instances to cancel
    final List<Long> flowNodeInstanceKeys;
    if (StringUtils.hasText(flowNodeInstanceKey)) {
      LOGGER.debug("Cancel token from flowNodeInstanceKey {} ", flowNodeInstanceKey);
      flowNodeInstanceKeys = List.of(Long.parseLong(flowNodeInstanceKey));
    } else {
      flowNodeInstanceKeys =
          flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
              processInstanceKey, modification.getFromFlowNodeId(), List.of(FlowNodeState.ACTIVE));
    }

    if (flowNodeInstanceKeys.isEmpty()) {
      throw new OperateRuntimeException(
          String.format(
              "Abort CANCEL_TOKEN: Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s",
              processInstanceKey, modification.getFromFlowNodeId()));
    }

    LOGGER.debug("Cancel token from flowNodeInstanceKeys {} ", flowNodeInstanceKeys);
    return cancelFlowNodeInstances(currentStep, flowNodeInstanceKeys);
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2
      cancelFlowNodeInstances(
          final ModifyProcessInstanceCommandStep1 currentStep,
          final List<Long> flowNodeInstanceKeysToCancel) {
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep = null;
    final Iterator<Long> iter = flowNodeInstanceKeysToCancel.iterator();
    while (iter.hasNext()) {
      nextStep = currentStep.terminateElement(iter.next());
      if (iter.hasNext()) {
        nextStep.and();
      }
    }
    return nextStep;
  }
}
