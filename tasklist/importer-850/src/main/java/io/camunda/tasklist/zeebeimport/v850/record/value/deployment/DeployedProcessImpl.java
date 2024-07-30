/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v850.record.value.deployment;

import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.Arrays;
import java.util.Objects;

public class DeployedProcessImpl implements Process {

  private String bpmnProcessId;
  private String resourceName;
  private long processDefinitionKey;
  private int version;
  private byte[] checksum;
  private byte[] resource;
  private boolean isDuplicate;
  private String tenantId;
  private long deploymentKey;

  public DeployedProcessImpl() {}

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
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
  public boolean isDuplicate() {
    return isDuplicate;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public void setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
  }

  public void setDuplicate(final boolean duplicate) {
    isDuplicate = duplicate;
  }

  public DeployedProcessImpl setChecksum(final byte[] checksum) {
    this.checksum = checksum;
    return this;
  }

  public void setResourceName(final String resourceName) {
    this.resourceName = resourceName;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setVersion(final int version) {
    this.version = version;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public byte[] getResource() {
    return resource;
  }

  public DeployedProcessImpl setResource(final byte[] resource) {
    this.resource = resource;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            bpmnProcessId,
            resourceName,
            processDefinitionKey,
            version,
            isDuplicate,
            tenantId,
            deploymentKey);
    result = 31 * result + Arrays.hashCode(checksum);
    result = 31 * result + Arrays.hashCode(resource);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeployedProcessImpl that = (DeployedProcessImpl) o;
    return processDefinitionKey == that.processDefinitionKey
        && version == that.version
        && isDuplicate == that.isDuplicate
        && deploymentKey == that.deploymentKey
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(checksum, that.checksum)
        && Arrays.equals(resource, that.resource);
  }

  @Override
  public String toString() {
    return "DeployedProcessImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", version="
        + version
        + ", checksum="
        + Arrays.toString(checksum)
        + ", resource="
        + Arrays.toString(resource)
        + ", isDuplicate="
        + isDuplicate
        + ", tenantId="
        + tenantId
        + ", deploymentKey="
        + deploymentKey
        + '}';
  }
}
