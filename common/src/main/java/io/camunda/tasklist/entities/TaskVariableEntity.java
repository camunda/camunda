/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.entities;

import java.util.Objects;

/** Represents variable with its value at the moment when task was completed. */
public class TaskVariableEntity extends TasklistZeebeEntity<TaskVariableEntity> {

  private String taskId;
  private String name;
  private String value;
  private String fullValue;
  private boolean isPreview;

  public TaskVariableEntity() {}

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

  public String getFullValue() {
    return fullValue;
  }

  public TaskVariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public boolean getIsPreview() {
    return isPreview;
  }

  public TaskVariableEntity setIsPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  public static TaskVariableEntity createFrom(
      String taskId, String name, String value, int variableSizeThreshold) {
    final TaskVariableEntity entity =
        new TaskVariableEntity().setId(getIdBy(taskId, name)).setTaskId(taskId).setName(name);
    if (value.length() > variableSizeThreshold) {
      // store preview
      entity.setValue(value.substring(0, variableSizeThreshold));
      entity.setIsPreview(true);
    } else {
      entity.setValue(value);
    }
    entity.setFullValue(value);
    return entity;
  }

  public static TaskVariableEntity createFrom(String taskId, VariableEntity variableEntity) {
    return new TaskVariableEntity()
        .setId(getIdBy(taskId, variableEntity.getName()))
        .setTaskId(taskId)
        .setName(variableEntity.getName())
        .setValue(variableEntity.getValue())
        .setIsPreview(variableEntity.getIsPreview())
        .setFullValue(variableEntity.getFullValue());
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
    return isPreview == that.isPreview
        && Objects.equals(taskId, that.taskId)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(fullValue, that.fullValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), taskId, name, value, fullValue, isPreview);
  }
}
