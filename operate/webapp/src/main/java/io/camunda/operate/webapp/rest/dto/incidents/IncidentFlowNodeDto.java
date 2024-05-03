/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

public class IncidentFlowNodeDto {

  private String id;

  private int count;

  public IncidentFlowNodeDto() {}

  public IncidentFlowNodeDto(String flowNodeId, int count) {
    this.id = flowNodeId;
    this.count = count;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + count;
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

    final IncidentFlowNodeDto that = (IncidentFlowNodeDto) o;

    if (count != that.count) {
      return false;
    }
    return id != null ? id.equals(that.id) : that.id == null;
  }
}
