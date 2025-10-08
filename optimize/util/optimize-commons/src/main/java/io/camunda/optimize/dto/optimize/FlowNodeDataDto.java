/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.io.Serializable;
import java.util.Objects;

public class FlowNodeDataDto implements Serializable {

  private String id;
  private String name;
  private String type;

  public FlowNodeDataDto() {}

  public FlowNodeDataDto(final String id, final String name, final String type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeDataDto that = (FlowNodeDataDto) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, type);
  }

  @Override
  public String toString() {
    return "FlowNodeDataDto(id=" + getId() + ", name=" + getName() + ", type=" + getType() + ")";
  }
}
