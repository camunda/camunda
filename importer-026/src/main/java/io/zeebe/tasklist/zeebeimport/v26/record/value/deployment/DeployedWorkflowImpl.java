/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v26.record.value.deployment;

import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import java.util.Objects;

public class DeployedWorkflowImpl implements DeployedWorkflow {

  private String bpmnProcessId;
  private String resourceName;
  private long workflowKey;
  private int version;

  public DeployedWorkflowImpl() {}

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public void setWorkflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(bpmnProcessId, resourceName, workflowKey, version);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeployedWorkflowImpl that = (DeployedWorkflowImpl) o;
    return workflowKey == that.workflowKey
        && version == that.version
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(resourceName, that.resourceName);
  }

  @Override
  public String toString() {
    return "DeployedWorkflowImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", workflowKey="
        + workflowKey
        + ", version="
        + version
        + '}';
  }
}
