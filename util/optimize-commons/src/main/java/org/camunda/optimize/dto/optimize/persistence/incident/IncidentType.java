/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.persistence.incident;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import static org.camunda.optimize.service.util.importing.EngineConstants.FAILED_EXTERNAL_TASK_INCIDENT_TYPE;
import static org.camunda.optimize.service.util.importing.EngineConstants.FAILED_JOB_INCIDENT_TYPE;

@Slf4j
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentType {

  /*
    Those are just the predefined incident types that are raised by the engine
    out of the box. However, it's possible to create custom incident types.
    For more, see:
    https://docs.camunda.org/manual/latest/user-guide/process-engine/incidents/#incident-types
   */
  private static final IncidentType FAILED_JOB = new IncidentType(FAILED_JOB_INCIDENT_TYPE);
  public static final IncidentType FAILED_EXTERNAL_TASK = new IncidentType(FAILED_EXTERNAL_TASK_INCIDENT_TYPE);

  private final String id;

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }

  public static IncidentType valueOfId(final String incidentTypeId) {
    if (incidentTypeId == null) {
      throw new OptimizeRuntimeException("Incident type not allowed to be null!");
    }
    if (!FAILED_JOB.getId().equals(incidentTypeId) && !FAILED_EXTERNAL_TASK.getId().equals(incidentTypeId)) {
      log.debug("Importing custom incident type [{}]", incidentTypeId);
    }
    return new IncidentType(incidentTypeId);
  }
}
