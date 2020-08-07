/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.entities;

import java.time.OffsetDateTime;
import java.util.Objects;

public class WorkflowInstanceEntity extends TasklistZeebeEntity<WorkflowInstanceEntity> {

  private WorkflowInstanceState state;
  private OffsetDateTime endDate;

  public WorkflowInstanceState getState() {
    return state;
  }

  public WorkflowInstanceEntity setState(final WorkflowInstanceState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public WorkflowInstanceEntity setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final WorkflowInstanceEntity that = (WorkflowInstanceEntity) o;
    return state == that.state && Objects.equals(endDate, that.endDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), state, endDate);
  }
}
