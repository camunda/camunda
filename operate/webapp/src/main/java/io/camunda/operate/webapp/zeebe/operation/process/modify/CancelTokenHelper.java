/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CancelTokenHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(CancelTokenHelper.class);

  public ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2
      cancelFlowNodeInstances(
          ModifyProcessInstanceCommandStep1 currentStep, final List<Long> flowNodeInstanceKeys) {
    LOGGER.debug("Move [Cancel token from flowNodeInstanceKeys: {} ]", flowNodeInstanceKeys);
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep = null;
    final int size = flowNodeInstanceKeys.size();
    for (int i = 0; i < size; i++) {
      if (i < size - 1) {
        currentStep = currentStep.terminateElement(flowNodeInstanceKeys.get(i)).and();
      } else {
        nextStep = currentStep.terminateElement(flowNodeInstanceKeys.get(i));
      }
    }
    return nextStep;
  }
}
