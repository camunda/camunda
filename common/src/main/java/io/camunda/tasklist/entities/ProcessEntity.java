/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProcessEntity extends TasklistZeebeEntity<ProcessEntity> {

  private String bpmnProcessId;

  private String name;

  private List<ProcessFlowNodeEntity> flowNodes = new ArrayList<>();

  public String getName() {
    return name;
  }

  public ProcessEntity setName(String name) {
    this.name = name;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
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
    return Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(name, that.name)
        && Objects.equals(flowNodes, that.flowNodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), bpmnProcessId, name, flowNodes);
  }
}
