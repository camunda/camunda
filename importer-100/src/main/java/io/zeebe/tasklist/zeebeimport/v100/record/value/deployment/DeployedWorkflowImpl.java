/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v100.record.value.deployment;

import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import java.util.Arrays;
import java.util.Objects;

public class DeployedWorkflowImpl implements DeployedWorkflow {

  private String bpmnProcessId;
  private String resourceName;
  private long workflowKey;
  private int version;
  private byte[] checksum;
  private byte[] resource;

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

  @Override
  public byte[] getChecksum() {
    return checksum;
  }

  @Override
  public byte[] getResource() {
    return resource;
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

  public DeployedWorkflowImpl setChecksum(final byte[] checksum) {
    this.checksum = checksum;
    return this;
  }

  public DeployedWorkflowImpl setResource(final byte[] resource) {
    this.resource = resource;
    return this;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public boolean equals(final Object o) {
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
        && Objects.equals(resourceName, that.resourceName)
        && Arrays.equals(checksum, that.checksum)
        && Arrays.equals(resource, that.resource);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(bpmnProcessId, resourceName, workflowKey, version);
    result = 31 * result + Arrays.hashCode(checksum);
    result = 31 * result + Arrays.hashCode(resource);
    return result;
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
        + ", checksum="
        + Arrays.toString(checksum)
        + ", resource="
        + Arrays.toString(resource)
        + '}';
  }
}
