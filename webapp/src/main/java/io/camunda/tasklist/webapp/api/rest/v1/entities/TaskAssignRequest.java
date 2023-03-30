/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.StringJoiner;

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
              + "must be first unassign and only then assign again. (Default: `true`)")
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
