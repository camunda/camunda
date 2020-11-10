/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportType {
  PROCESS,
  DECISION,
  ;

  @JsonValue
  public String getId() {
    return name().toLowerCase();
  }

  @Override
  public String toString() {
    return getId();
  }

  public DefinitionType toDefinitionType() {
    return DefinitionType.fromString(this.name());
  }

  // This is used by jersey on unmarshalling query/path parameters
  // see https://docs.jboss.org/resteasy/docs/3.5.0.Final/userguide/html/StringConverter.html#d4e1541
  public static ReportType fromString(final String name) {
    return valueOf(name.toUpperCase());
  }
}
