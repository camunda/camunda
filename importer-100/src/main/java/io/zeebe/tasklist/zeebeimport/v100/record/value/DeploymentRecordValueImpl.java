/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v100.record.value;

import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.zeebe.tasklist.zeebeimport.v100.record.value.deployment.DeployedWorkflowImpl;
import io.zeebe.tasklist.zeebeimport.v100.record.value.deployment.DeploymentResourceImpl;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DeploymentRecordValueImpl implements DeploymentRecordValue, RecordValue {

  private List<DeployedWorkflowImpl> deployedWorkflows;
  private List<DeploymentResourceImpl> resources;

  public DeploymentRecordValueImpl() {}

  @Override
  public List<DeploymentResource> getResources() {
    return Arrays.asList(resources.toArray(new DeploymentResourceImpl[resources.size()]));
  }

  @Override
  public List<DeployedWorkflow> getDeployedWorkflows() {
    return Arrays.asList(deployedWorkflows.toArray(new DeployedWorkflow[deployedWorkflows.size()]));
  }

  public void setDeployedWorkflows(List<DeployedWorkflowImpl> deployedWorkflows) {
    this.deployedWorkflows = deployedWorkflows;
  }

  public void setResources(List<DeploymentResourceImpl> resources) {
    this.resources = resources;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public int hashCode() {
    return Objects.hash(deployedWorkflows, resources);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeploymentRecordValueImpl that = (DeploymentRecordValueImpl) o;
    return Objects.equals(deployedWorkflows, that.deployedWorkflows)
        && Objects.equals(resources, that.resources);
  }

  @Override
  public String toString() {
    return "DeploymentRecordValueImpl{"
        + "deployedWorkflows="
        + deployedWorkflows
        + ", resources="
        + resources
        + '}';
  }
}
