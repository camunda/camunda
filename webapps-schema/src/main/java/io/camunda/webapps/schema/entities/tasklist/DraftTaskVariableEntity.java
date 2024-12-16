/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.tasklist;

import java.util.Objects;

/** Represents draft variable with its value when task is in created state. */
public class DraftTaskVariableEntity extends TasklistEntity<DraftTaskVariableEntity> {

  private String taskId;
  private String name;
  private String value;
  private String fullValue;
  private boolean isPreview;

  public String getTaskId() {
    return taskId;
  }

  public DraftTaskVariableEntity setTaskId(final String taskId) {
    this.taskId = taskId;
    return this;
  }

  public String getName() {
    return name;
  }

  public DraftTaskVariableEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public DraftTaskVariableEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public DraftTaskVariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public boolean getIsPreview() {
    return isPreview;
  }

  public DraftTaskVariableEntity setIsPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), taskId, name, value, fullValue, isPreview);
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
    final DraftTaskVariableEntity that = (DraftTaskVariableEntity) o;
    return isPreview == that.isPreview
        && Objects.equals(taskId, that.taskId)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(fullValue, that.fullValue);
  }
}
