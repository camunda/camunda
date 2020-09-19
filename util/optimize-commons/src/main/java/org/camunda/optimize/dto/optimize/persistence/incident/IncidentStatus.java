/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.persistence.incident;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IncidentStatus {

  OPEN,
  DELETED,
  RESOLVED;

  @JsonValue
  public String getId() {
    return name().toLowerCase();
  }

  @Override
  public String toString() {
    return getId();
  }

  public static IncidentStatus valueOfId(final String id) {
    return valueOf(id.toUpperCase());
  }
}
