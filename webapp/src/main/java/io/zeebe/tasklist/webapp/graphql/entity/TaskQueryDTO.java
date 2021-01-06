/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.entity;

import io.zeebe.tasklist.entities.TaskState;

public class TaskQueryDTO {

  private TaskState state;
  private Boolean assigned;
  private String assignee;

  public TaskState getState() {
    return state;
  }

  public TaskQueryDTO setState(TaskState state) {
    this.state = state;
    return this;
  }

  public Boolean getAssigned() {
    return assigned;
  }

  public TaskQueryDTO setAssigned(Boolean assigned) {
    this.assigned = assigned;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskQueryDTO setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  @Override
  public int hashCode() {
    int result = state != null ? state.hashCode() : 0;
    result = 31 * result + (assigned != null ? assigned.hashCode() : 0);
    result = 31 * result + (assignee != null ? assignee.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final TaskQueryDTO taskQuery = (TaskQueryDTO) o;

    if (state != taskQuery.state) {
      return false;
    }
    if (assigned != null ? !assigned.equals(taskQuery.assigned) : taskQuery.assigned != null) {
      return false;
    }
    return assignee != null ? assignee.equals(taskQuery.assignee) : taskQuery.assignee == null;
  }
}
