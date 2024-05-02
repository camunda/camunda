/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CancelTokenHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(CancelTokenHandler.class);
  private final FlowNodeInstanceReader flowNodeInstanceReader;
  private final CancelTokenHelper cancelTokenHelper;

  public CancelTokenHandler(
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final CancelTokenHelper cancelTokenHelper) {
    this.flowNodeInstanceReader = flowNodeInstanceReader;
    this.cancelTokenHelper = cancelTokenHelper;
  }

  public ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 cancelToken(
      final ModifyProcessInstanceCommandStep1 currentStep,
      final Long processInstanceKey,
      final Modification modification) {
    final String flowNodeId = modification.getFromFlowNodeId();
    final String flowNodeInstanceKeyAsString = modification.getFromFlowNodeInstanceKey();
    if (StringUtils.hasText(flowNodeInstanceKeyAsString)) {
      final Long flowNodeInstanceKey = Long.parseLong(flowNodeInstanceKeyAsString);
      LOGGER.debug("Cancel token from flowNodeInstanceKey {} ", flowNodeInstanceKey);
      return cancelTokenHelper.cancelFlowNodeInstances(currentStep, List.of(flowNodeInstanceKey));
    } else {
      final List<Long> flowNodeInstanceKeys =
          flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
              processInstanceKey, flowNodeId, List.of(FlowNodeState.ACTIVE));
      if (flowNodeInstanceKeys.isEmpty()) {
        throw new OperateRuntimeException(
            String.format(
                "Abort CANCEL_TOKEN: Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s",
                processInstanceKey, flowNodeId));
      }
      LOGGER.debug("Cancel token from flowNodeInstanceKeys {} ", flowNodeInstanceKeys);
      return cancelTokenHelper.cancelFlowNodeInstances(currentStep, flowNodeInstanceKeys);
    }
  }
}
