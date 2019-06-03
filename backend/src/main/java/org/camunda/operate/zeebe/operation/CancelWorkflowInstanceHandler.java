/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.exceptions.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientException;

/**
 * Operation handler to cancel workflow instances.
 */
@Component
public class CancelWorkflowInstanceHandler extends AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(CancelWorkflowInstanceHandler.class);

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ZeebeClient zeebeClient;

  @Override
  public void handleWithException(OperationEntity operation) throws PersistenceException {
    if (operation.getWorkflowInstanceId() == null) {
      failOperation(operation, "No workflow instance id is provided.");
    }
    final WorkflowInstanceForListViewEntity workflowInstance = workflowInstanceReader.getWorkflowInstanceById(operation.getWorkflowInstanceId());

    if (!workflowInstance.getState().equals(WorkflowInstanceState.ACTIVE) && !workflowInstance.getState().equals(WorkflowInstanceState.INCIDENT)) {
      //fail operation
      failOperation(operation, String.format("Unable to cancel %s workflow instance. Instance must be in ACTIVE or INCIDENT state.", workflowInstance.getState()));
      return;
    }

    try {
      zeebeClient.newCancelInstanceCommand(workflowInstance.getKey()).send().join();
      //mark operation as sent
      markAsSent(operation);
    } catch (ClientException ex) {
      logger.error("Zeebe command rejected: " + ex.getMessage(), ex);
      //fail operation
      failOperation(operation, ex.getMessage());
    }

  }

  @Override
  public OperationType getType() {
    return OperationType.CANCEL_WORKFLOW_INSTANCE;
  }
}
