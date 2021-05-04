/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.zeebe.operation;

import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.SetVariablesResponse;

/**
 * Update the variable.
 */
@Component
public class UpdateVariableHandler extends AbstractOperationHandler implements OperationHandler {

  @Autowired
  private ZeebeClient zeebeClient;

  @Override
  public void handleWithException(OperationEntity operation) throws Exception {
    String updateVariableJson = mergeVariableJson(operation.getVariableName(), operation.getVariableValue());
    SetVariablesResponse response =
        zeebeClient
            .newSetVariablesCommand(operation.getScopeKey())
            .variables(updateVariableJson)
            .local(true)
            .send().join();
    markAsSent(operation, response.getKey());
  }

  private String mergeVariableJson(String variableName, String variableValue) {
    return String.format("{\"%s\":%s}", variableName, variableValue);
  }

  @Override
  public OperationType getType() {
    return OperationType.UPDATE_VARIABLE;
  }

  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }
}
