/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientException;

/**
 * Update the variable.
 */
@Component
public class UpdateVariableHandler extends AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(UpdateVariableHandler.class);

  @Autowired
  private ZeebeClient zeebeClient;

  @Override
  public void handleWithException(OperationEntity operation) throws PersistenceException {
    try {
      String updateVariableJson = mergeVariableJson(operation.getVariableName(), operation.getVariableValue());
      zeebeClient.newSetVariablesCommand(IdUtil.getKey(operation.getScopeId()))
        .variables(updateVariableJson)
        .local(true)
        .send().join();
      markAsSent(operation);
    } catch (ClientException ex) {
      logger.error("Zeebe command rejected: " + ex.getMessage(), ex);
      //fail operation
      failOperation(operation, ex.getMessage());
    }
  }

  private String mergeVariableJson(String variableName, String variableValue) {
    return String.format("{\"%s\":%s}", variableName, variableValue);
  }

  @Override
  public OperationType getType() {
    return OperationType.UPDATE_VARIABLE;
  }
}
