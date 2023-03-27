/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

public class TaskOrderByDTO {
  private TaskSortFields field;
  private Sort order;

  public TaskSortFields getField() {
    return field;
  }

  public TaskOrderByDTO setField(TaskSortFields field) {
    this.field = field;
    return this;
  }

  public Sort getOrder() {
    return order;
  }

  public TaskOrderByDTO setOrder(Sort order) {
    this.order = order;
    return this;
  }
}
