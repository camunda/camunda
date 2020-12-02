/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportConstants;

@AllArgsConstructor
public enum FilterApplicationLevel {
  INSTANCE(ReportConstants.INSTANCE),
  VIEW(ReportConstants.VIEW);

  private final String id;

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }

}
