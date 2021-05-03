/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.record.value.deployment;

import java.util.Arrays;
import java.util.Objects;
import io.zeebe.protocol.record.value.deployment.Process;

public class DeployedProcessImpl implements Process {
  private String bpmnProcessId;
  private String resourceName;
  private long processDefinitionKey;
  private int version;
  private byte[] checksum;
  private byte[] resource;

  public DeployedProcessImpl() {
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public byte[] getChecksum() {
    return checksum;
  }

  @Override
  public byte[] getResource() {
    return resource;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public DeployedProcessImpl setChecksum(final byte[] checksum) {
    this.checksum = checksum;
    return this;
  }

  public DeployedProcessImpl setResource(final byte[] resource) {
    this.resource = resource;
    return this;
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
    return processDefinitionKey == that.processDefinitionKey &&
        version == that.version &&
        Objects.equals(bpmnProcessId, that.bpmnProcessId) &&
        Objects.equals(resourceName, that.resourceName) &&
        Arrays.equals(checksum, that.checksum) &&
        Arrays.equals(resource, that.resource);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(bpmnProcessId, resourceName, processDefinitionKey, version);
    result = 31 * result + Arrays.hashCode(checksum);
    result = 31 * result + Arrays.hashCode(resource);
    return result;
  }

  @Override
  public String toString() {
    return "DeployedProcessImpl{" +
        "bpmnProcessId='" + bpmnProcessId + '\'' +
        ", resourceName='" + resourceName + '\'' +
        ", processDefinitionKey=" + processDefinitionKey +
        ", version=" + version +
        ", checksum=" + Arrays.toString(checksum) +
        ", resource=" + Arrays.toString(resource) +
        '}';
  }
}
