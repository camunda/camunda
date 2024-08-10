/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.StringJoiner;

@Schema(description = "Request params used to assign the task to assignee or current user.")
public class TaskAssignRequest {
  @Schema(
      description =
          "When using a JWT token, the assignee parameter is NOT optional when called directly from the API.\n"
              + "The system will not be able to detect the assignee from the JWT token, therefore the assignee parameter needs to be\n"
              + "explicitly passed in this instance.")
  private String assignee;

  @Schema(
      description =
          "When `true` the task that is already assigned may be assigned again. Otherwise the task\n"
              + "must be first unassigned and only then assigned again.",
      defaultValue = "true")
  private Boolean allowOverrideAssignment = true;

  public String getAssignee() {
    return assignee;
  }

  public TaskAssignRequest setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public boolean isAllowOverrideAssignment() {
    return allowOverrideAssignment;
  }

  public TaskAssignRequest setAllowOverrideAssignment(boolean allowOverrideAssignment) {
    this.allowOverrideAssignment = allowOverrideAssignment;
    return this;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskAssignRequest.class.getSimpleName() + "[", "]")
        .add("assignee='" + assignee + "'")
        .add("allowOverrideAssignment=" + allowOverrideAssignment)
        .toString();
  }
}
