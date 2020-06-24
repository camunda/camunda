/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es.cache;

import io.zeebe.tasklist.entities.WorkflowEntity;
import io.zeebe.tasklist.entities.WorkflowFlowNodeEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class WorkflowCacheEntity {

  private String name;

  private Map<String, String> flowNodeNames = new HashMap<>();

  public String getName() {
    return name;
  }

  public WorkflowCacheEntity setName(String name) {
    this.name = name;
    return this;
  }

  public Map<String, String> getFlowNodeNames() {
    return flowNodeNames;
  }

  public WorkflowCacheEntity setFlowNodeNames(Map<String, String> flowNodeNames) {
    this.flowNodeNames = flowNodeNames;
    return this;
  }

  public static WorkflowCacheEntity createFrom(WorkflowEntity workflowEntity) {
    return new WorkflowCacheEntity()
        .setName(workflowEntity.getName())
        .setFlowNodeNames(
            workflowEntity.getFlowNodes().stream()
                .collect(
                    Collectors.toMap(
                        WorkflowFlowNodeEntity::getId, WorkflowFlowNodeEntity::getName)));
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (flowNodeNames != null ? flowNodeNames.hashCode() : 0);
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

    final WorkflowCacheEntity that = (WorkflowCacheEntity) o;

    if (name != null ? !name.equals(that.name) : that.name != null) {
      return false;
    }
    return flowNodeNames != null
        ? flowNodeNames.equals(that.flowNodeNames)
        : that.flowNodeNames == null;
  }
}
