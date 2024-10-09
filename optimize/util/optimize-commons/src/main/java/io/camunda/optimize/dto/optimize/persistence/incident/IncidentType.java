/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.persistence.incident;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.FAILED_EXTERNAL_TASK_INCIDENT_TYPE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FAILED_JOB_INCIDENT_TYPE;

import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.slf4j.Logger;

public class IncidentType {

  public static final IncidentType FAILED_EXTERNAL_TASK =
      new IncidentType(FAILED_EXTERNAL_TASK_INCIDENT_TYPE);
  /*
   Those are just the predefined incident types that are raised by the engine
   out of the box. However, it's possible to create custom incident types.
   For more, see:
   https://docs.camunda.org/manual/latest/user-guide/process-engine/incidents/#incident-types
  */
  private static final IncidentType FAILED_JOB = new IncidentType(FAILED_JOB_INCIDENT_TYPE);
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(IncidentType.class);
  private final String id;

  protected IncidentType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  public static IncidentType valueOfId(final String incidentTypeId) {
    if (incidentTypeId == null) {
      throw new OptimizeRuntimeException("Incident type not allowed to be null!");
    }
    if (!FAILED_JOB.getId().equals(incidentTypeId)
        && !FAILED_EXTERNAL_TASK.getId().equals(incidentTypeId)) {
      log.debug("Importing custom incident type [{}]", incidentTypeId);
    }
    return new IncidentType(incidentTypeId);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IncidentType;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IncidentType)) {
      return false;
    }
    final IncidentType other = (IncidentType) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return getId();
  }
}
