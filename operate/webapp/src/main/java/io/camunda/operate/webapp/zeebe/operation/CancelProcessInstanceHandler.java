/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.webapps.schema.entities.operation.OperationType.CANCEL_PROCESS_INSTANCE;

import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Operation handler to cancel process instances. */
@Component
public class CancelProcessInstanceHandler extends AbstractOperationHandler
    implements OperationHandler {

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {
    if (operation.getProcessInstanceKey() == null) {
      failOperation(operation, "No process instance id is provided.");
      return;
    }
    final ProcessInstanceForListViewEntity processInstance =
        processInstanceReader.getProcessInstanceByKey(operation.getProcessInstanceKey());

    if (!processInstance.getState().equals(ProcessInstanceState.ACTIVE)) {
      // fail operation
      failOperation(
          operation,
          String.format(
              "Unable to cancel %s process instance. Instance must be in ACTIVE or INCIDENT state.",
              processInstance.getState()));
      return;
    }

    final String id = operation.getId();
    final var cancelInstanceCommand =
        withOperationReference(
            camundaClient.newCancelInstanceCommand(processInstance.getKey()), id);
    cancelInstanceCommand.send().join();

    // mark operation as sent
    markAsSent(operation);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(CANCEL_PROCESS_INSTANCE);
  }
}
