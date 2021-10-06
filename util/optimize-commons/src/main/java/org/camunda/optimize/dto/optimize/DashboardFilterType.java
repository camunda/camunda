/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum DashboardFilterType {
  START_DATE("startDate"),
  END_DATE("endDate"),
  STATE("state"),
  VARIABLE("variable"),
  ASSIGNEE("assignee"),
  CANDIDATE_GROUP("candidateGroup");

  private String id;

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }

}
