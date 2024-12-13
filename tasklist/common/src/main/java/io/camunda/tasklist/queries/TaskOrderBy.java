/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.queries;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.StringJoiner;

@Schema(description = "Sort results by a specific field.")
public class TaskOrderBy {

  private TaskSortFields field;

  @Schema(description = "* `ASC`: Ascending<br>" + "* `DESC`: Descending")
  private Sort order;

  public TaskOrderBy(final TaskSortFields field, final Sort order) {
    this.field = field;
    this.order = order;
  }

  public TaskOrderBy() {}

  public TaskSortFields getField() {
    return field;
  }

  public TaskOrderBy setField(TaskSortFields field) {
    this.field = field;
    return this;
  }

  public Sort getOrder() {
    return order;
  }

  public TaskOrderBy setOrder(Sort order) {
    this.order = order;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, order);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskOrderBy that = (TaskOrderBy) o;
    return field == that.field && order == that.order;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskOrderBy.class.getSimpleName() + "[", "]")
        .add("field=" + field)
        .add("order=" + order)
        .toString();
  }
}
