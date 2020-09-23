/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.persistence.incident;

import com.fasterxml.jackson.annotation.JsonValue;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import static org.camunda.optimize.service.util.importing.EngineConstants.FAILED_EXTERNAL_TASK_INCIDENT_TYPE;
import static org.camunda.optimize.service.util.importing.EngineConstants.FAILED_JOB_INCIDENT_TYPE;

public enum IncidentType {

  FAILED_JOB(FAILED_JOB_INCIDENT_TYPE),
  FAILED_EXTERNAL_TASK(FAILED_EXTERNAL_TASK_INCIDENT_TYPE);

  private final String id;

  IncidentType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }

  public static IncidentType valueOfId(final String incidentTypeId) {
    for(IncidentType e : values()) {
      if(e.id.equals(incidentTypeId)) {
        return e;
      }
    }
    throw new OptimizeRuntimeException(String.format("Unknown incident type [%s]", incidentTypeId));
  }
}
