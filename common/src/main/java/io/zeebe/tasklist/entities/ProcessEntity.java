/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.entities;

import java.util.ArrayList;
import java.util.List;

public class ProcessEntity extends TasklistZeebeEntity<ProcessEntity> {

  private String name;

  private List<ProcessFlowNodeEntity> flowNodes = new ArrayList<>();

  public String getName() {
    return name;
  }

  public ProcessEntity setName(String name) {
    this.name = name;
    return this;
  }

  public List<ProcessFlowNodeEntity> getFlowNodes() {
    return flowNodes;
  }

  public ProcessEntity setFlowNodes(List<ProcessFlowNodeEntity> flowNodes) {
    this.flowNodes = flowNodes;
    return this;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (flowNodes != null ? flowNodes.hashCode() : 0);
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
    if (!super.equals(o)) {
      return false;
    }

    final ProcessEntity that = (ProcessEntity) o;

    if (name != null ? !name.equals(that.name) : that.name != null) {
      return false;
    }
    return flowNodes != null ? flowNodes.equals(that.flowNodes) : that.flowNodes == null;
  }
}
