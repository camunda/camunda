/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import java.util.Objects;

public class ProcessFlowNodeEntity {

  private String id;
  private String name;

  public ProcessFlowNodeEntity() {}

  public ProcessFlowNodeEntity(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public ProcessFlowNodeEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessFlowNodeEntity setName(final String name) {
    this.name = name;
    return this;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ProcessFlowNodeEntity that = (ProcessFlowNodeEntity) o;

    if (!Objects.equals(id, that.id)) {
      return false;
    }
    return Objects.equals(name, that.name);
  }
}
