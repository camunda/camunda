/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.cache;

import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.ProcessFlowNodeEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ProcessCacheEntity {

  private String name;

  private Map<String, String> flowNodeNames = new HashMap<>();

  public String getName() {
    return name;
  }

  public ProcessCacheEntity setName(String name) {
    this.name = name;
    return this;
  }

  public Map<String, String> getFlowNodeNames() {
    return flowNodeNames;
  }

  public ProcessCacheEntity setFlowNodeNames(Map<String, String> flowNodeNames) {
    this.flowNodeNames = flowNodeNames;
    return this;
  }

  public static ProcessCacheEntity createFrom(ProcessEntity processEntity) {
    return new ProcessCacheEntity()
        .setName(processEntity.getName())
        .setFlowNodeNames(
            processEntity.getFlowNodes().stream()
                .filter(n -> Objects.nonNull(n.getName()))
                .collect(
                    Collectors.toMap(
                        ProcessFlowNodeEntity::getId, ProcessFlowNodeEntity::getName)));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ProcessCacheEntity that = (ProcessCacheEntity) o;

    if (name != null ? !name.equals(that.name) : that.name != null) {
      return false;
    }
    return flowNodeNames != null
        ? flowNodeNames.equals(that.flowNodeNames)
        : that.flowNodeNames == null;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, flowNodeNames);
  }
}
