/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.WorkflowEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("Workflow object")
public class WorkflowDto {

  @ApiModelProperty(value = "Unique id of the workflow, must be used when filtering instances by workflow ids.")
  private String id;
  private String name;
  private int version;
  private String bpmnProcessId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public static WorkflowDto createFrom(WorkflowEntity workflowEntity) {
    if (workflowEntity == null) {
      return null;
    }
    WorkflowDto workflow = new WorkflowDto();
    workflow.setId(workflowEntity.getId());
    workflow.setBpmnProcessId(workflowEntity.getBpmnProcessId());
    workflow.setName(workflowEntity.getName());
    workflow.setVersion(workflowEntity.getVersion());
    return workflow;
  }

  public static List<WorkflowDto> createFrom(List<WorkflowEntity> workflowEntities) {
    List<WorkflowDto> result = new ArrayList<>();
    if (workflowEntities != null) {
      for (WorkflowEntity workflowEntity: workflowEntities) {
        if (workflowEntity != null) {
          result.add(createFrom(workflowEntity));
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    WorkflowDto that = (WorkflowDto) o;

    if (version != that.version)
      return false;
    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    return bpmnProcessId != null ? bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    return result;
  }
}
