/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.webapps.schema.entities.operation.OperationType.RESOLVE_INCIDENT;

import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Resolve the incident. */
@Component
public class ResolveIncidentHandler extends AbstractOperationHandler implements OperationHandler {

  @Autowired private IncidentReader incidentReader;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {

    if (operation.getIncidentKey() == null) {
      failOperation(operation, "Incident key must be defined.");
      return;
    }

    final IncidentEntity incident;
    try {
      incident = incidentReader.getIncidentById(operation.getIncidentKey());
    } catch (final NotFoundException ex) {
      failOperation(operation, incidentNotFoundMessage(operation));
      return;
    }

    // The incident must belong to this process instance (fail closed on missing keys). The same
    // message is used for "not found" and "does not belong" so the caller cannot tell them apart.
    if (operation.getProcessInstanceKey() == null
        || !operation.getProcessInstanceKey().equals(incident.getProcessInstanceKey())) {
      failOperation(operation, incidentNotFoundMessage(operation));
      return;
    }

    final ErrorType errorType = incident.getErrorType();
    if (errorType != null && errorType.isResolvedViaRetries()) {
      operationServicesAdapter.updateJobRetries(incident.getJobKey(), 1, operation.getId());
    }
    operationServicesAdapter.resolveIncident(incident.getKey(), operation.getId());
    // mark operation as sent
    markAsSent(operation);
  }

  private static String incidentNotFoundMessage(final OperationEntity operation) {
    return String.format(
        "No incident %s found for process instance %s.",
        operation.getIncidentKey(), operation.getProcessInstanceKey());
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(RESOLVE_INCIDENT);
  }
}
