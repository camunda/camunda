/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.operate.entities.OperationType.DELETE_PROCESS_INSTANCE;

import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.es.writer.ProcessInstanceWriter;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
/**
 * Operation handler to delete process instances.
 */
@Component
public class DeleteProcessInstanceHandler extends AbstractOperationHandler implements OperationHandler{

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private ProcessInstanceWriter processInstanceWriter;

  @Autowired
  private OperationsManager operationsManager;

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

  private void completeOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.completeOperation(operation);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(DELETE_PROCESS_INSTANCE);
  }
}
