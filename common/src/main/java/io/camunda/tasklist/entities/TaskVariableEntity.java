/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.entities;

import java.util.Objects;

/** Represents variable with its value at the moment when task was completed. */
public class TaskVariableEntity extends TasklistZeebeEntity<TaskVariableEntity> {

  private String taskId;
  private String name;
  private String value;

  public TaskVariableEntity() {}

  public TaskVariableEntity(final String taskId, final String name, final String value) {
    this.setId(getIdBy(taskId, name));
    this.taskId = taskId;
    this.name = name;
    this.value = value;
  }

  public static String getIdBy(String taskId, String name) {
    return String.format("%s-%s", taskId, name);
  }

  public String getTaskId() {
    return taskId;
  }

  public TaskVariableEntity setTaskId(final String taskId) {
    this.taskId = taskId;
    return this;
  }

  public String getName() {
    return name;
  }

  public TaskVariableEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public TaskVariableEntity setValue(final String value) {
    this.value = value;
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
    final TaskVariableEntity that = (TaskVariableEntity) o;
    return Objects.equals(taskId, that.taskId)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), taskId, name, value);
  }
}
