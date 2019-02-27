/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import java.util.List;
import java.util.stream.Collectors;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.exceptions.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientException;

/**
 * Updates retries for all jobs, that has related incidents.
 */
@Component
public class UpdateRetriesHandler extends AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(UpdateRetriesHandler.class);

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ZeebeClient zeebeClient;

  @Override
  public void handle(OperationEntity operation) throws PersistenceException {
    //FIXME
//    final WorkflowInstanceEntity workflowInstance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
//
//    List<IncidentEntity> incidentsToResolve =
//      workflowInstance.getIncidents().stream()
//        .filter(inc -> inc.getState().equals(IncidentState.ACTIVE) && inc.getErrorType().equals(IncidentEntity.JOB_NO_RETRIES_ERROR_TYPE))
//        .collect(Collectors.toList());
//
//    if (incidentsToResolve.size() == 0) {
//      //fail operation
//      failOperationsOfCurrentType(workflowInstance, "No appropriate incidents found.");
//    }
//
//    for (IncidentEntity incident : incidentsToResolve) {
//      try {
//        zeebeClient.newUpdateRetriesCommand(incident.getJobKey()).retries(1).send().join();
//        zeebeClient.newResolveIncidentCommand(incident.getKey()).send().join();
//        //mark operation as sent
//        markAsSentOperationsOfCurrentType(workflowInstance);
//      } catch (ClientException ex) {
//        logger.error("Zeebe command rejected: " + ex.getMessage(), ex);
//        //fail operation
//        failOperationsOfCurrentType(workflowInstance, ex.getMessage());
//      }
//    }

  }

  @Override
  public OperationType getType() {
    return OperationType.UPDATE_JOB_RETRIES;
  }
}
