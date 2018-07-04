package org.camunda.operate.rest.dto;

import org.camunda.operate.entities.WorkflowEntity;


public class WorkflowDto {

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

}
