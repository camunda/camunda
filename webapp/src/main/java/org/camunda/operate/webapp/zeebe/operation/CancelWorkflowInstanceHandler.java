/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.zeebe.operation;

import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.webapp.es.reader.WorkflowInstanceReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;

/**
 * Operation handler to cancel workflow instances.
 */
@Component
public class CancelWorkflowInstanceHandler extends AbstractOperationHandler implements OperationHandler {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ZeebeClient zeebeClient;

  @Override
  public void handleWithException(OperationEntity operation) throws Exception {
    if (operation.getWorkflowInstanceKey() == null) {
      failOperation(operation, "No workflow instance id is provided.");
      return;
    }
    final WorkflowInstanceForListViewEntity workflowInstance = workflowInstanceReader.getWorkflowInstanceByKey(operation.getWorkflowInstanceKey());

    if (!workflowInstance.getState().equals(WorkflowInstanceState.ACTIVE) && !workflowInstance.getState().equals(WorkflowInstanceState.INCIDENT)) {
      //fail operation
      failOperation(operation,
          String.format("Unable to cancel %s workflow instance. Instance must be in ACTIVE or INCIDENT state.", workflowInstance.getState()));
      return;
    }
    zeebeClient.newCancelInstanceCommand(workflowInstance.getKey()).send().join();
    //mark operation as sent
    markAsSent(operation);
  }


  @Override
  public OperationType getType() {
    return OperationType.CANCEL_WORKFLOW_INSTANCE;
  }

  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }
}
