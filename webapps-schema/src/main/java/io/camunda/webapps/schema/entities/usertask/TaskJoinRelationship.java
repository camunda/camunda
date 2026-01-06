/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usertask;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

public class TaskJoinRelationship {
  private String name;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long parent;

  public TaskJoinRelationship() {}

  public TaskJoinRelationship(final String name) {
    this.name = name;
  }

  public TaskJoinRelationship(final String name, final Long parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public Long getParent() {
    return parent;
  }

  public void setParent(final Long parent) {
    this.parent = parent;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, parent);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskJoinRelationship that = (TaskJoinRelationship) o;
    return Objects.equals(name, that.name) && Objects.equals(parent, that.parent);
  }

  public enum TaskJoinRelationshipType {
    PROCESS("process"),
    LOCAL_VARIABLE("localVariable"),
    PROCESS_VARIABLE("variable"),
    TASK("task");

    private final String type;

    TaskJoinRelationshipType(final String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }
}
