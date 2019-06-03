/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientException;
import static io.zeebe.protocol.ErrorType.JOB_NO_RETRIES;

/**
 * Resolve the incident.
 */
@Component
public class ResolveIncidentHandler extends AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(ResolveIncidentHandler.class);

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private ZeebeClient zeebeClient;

  @Override
  public void handleWithException(OperationEntity operation) throws PersistenceException {

    if (operation.getIncidentId() == null) {
      failOperation(operation, "Incident id must be defined.");
      return;
    }

    IncidentEntity incident = null;
    try {
      incident = incidentReader.getIncidentById(operation.getIncidentId());
    } catch (NotFoundException ex) {
      failOperation(operation, "No appropriate incidents found: " + ex.getMessage());
      return;
    }

    try {
      if (incident.getErrorType().equals(JOB_NO_RETRIES)) {
        zeebeClient.newUpdateRetriesCommand(IdUtil.getKey(incident.getJobId())).retries(1).send().join();
      }
      zeebeClient.newResolveIncidentCommand(incident.getKey()).send().join();
      // mark operation as sent
      markAsSent(operation);
    } catch (ClientException ex) {
      logger.error("Zeebe command rejected: " + ex.getMessage(), ex);
      // fail operation
      failOperation(operation, ex.getMessage());
    }

  }

  @Override
  public OperationType getType() {
    return OperationType.RESOLVE_INCIDENT;
  }
}
