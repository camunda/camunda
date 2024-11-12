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
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(IncidentType.class);
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
      LOG.debug("Importing custom incident type [{}]", incidentTypeId);
    }
    return new IncidentType(incidentTypeId);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IncidentType;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return getId();
  }
}
