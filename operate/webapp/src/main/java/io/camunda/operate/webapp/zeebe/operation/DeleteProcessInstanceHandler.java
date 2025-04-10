/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.webapps.schema.entities.operation.OperationType.DELETE_PROCESS_INSTANCE;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Operation handler to delete process instances. */
@Component
public class DeleteProcessInstanceHandler extends AbstractOperationHandler
    implements OperationHandler {

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private ProcessInstanceWriter processInstanceWriter;

  @Autowired private OperationsManager operationsManager;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {
    if (operation.getProcessInstanceKey() == null) {
      failOperation(operation, "No process instance id is provided.");
      return;
    }
    markAsSent(operation);
    final ProcessInstanceForListViewEntity processInstance =
        processInstanceReader.getProcessInstanceByKey(operation.getProcessInstanceKey());
    final Long processInstanceKey = processInstance.getProcessInstanceKey();
    processInstanceWriter.deleteInstanceById(processInstanceKey);
    completeOperation(operation);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(DELETE_PROCESS_INSTANCE);
  }

  @Override
  protected boolean canForceFailOperation(final OperationEntity operation) {
    return operation.getState().equals(OperationState.SENT);
  }

  private void completeOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.completeOperation(operation);
  }
}
