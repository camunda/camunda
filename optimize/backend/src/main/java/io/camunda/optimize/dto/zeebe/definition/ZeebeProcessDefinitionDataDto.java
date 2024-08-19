/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.definition;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import org.apache.commons.lang3.StringUtils;

public class ZeebeProcessDefinitionDataDto implements ProcessMetadataValue {

  private byte[] resource;
  private long processDefinitionKey;
  private int version;
  private byte[] checksum;
  private String resourceName;
  private String bpmnProcessId;
  private String tenantId;

  public ZeebeProcessDefinitionDataDto() {}

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public String getTenantId() {
    return StringUtils.isEmpty(tenantId) ? ZEEBE_DEFAULT_TENANT_ID : tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public byte[] getResource() {
    return resource;
  }

  public void setResource(final byte[] resource) {
    this.resource = resource;
  }

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
    // Process Records should never be duplicate in Zeebe
    return false;
  }

  @Override
  public long getDeploymentKey() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  public void setChecksum(final byte[] checksum) {
    this.checksum = checksum;
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

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeProcessDefinitionDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + java.util.Arrays.hashCode(getResource());
    final long $processDefinitionKey = getProcessDefinitionKey();
    result = result * PRIME + (int) ($processDefinitionKey >>> 32 ^ $processDefinitionKey);
    result = result * PRIME + getVersion();
    result = result * PRIME + java.util.Arrays.hashCode(getChecksum());
    final Object $resourceName = getResourceName();
    result = result * PRIME + ($resourceName == null ? 43 : $resourceName.hashCode());
    final Object $bpmnProcessId = getBpmnProcessId();
    result = result * PRIME + ($bpmnProcessId == null ? 43 : $bpmnProcessId.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeProcessDefinitionDataDto)) {
      return false;
    }
    final ZeebeProcessDefinitionDataDto other = (ZeebeProcessDefinitionDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!java.util.Arrays.equals(getResource(), other.getResource())) {
      return false;
    }
    if (getProcessDefinitionKey() != other.getProcessDefinitionKey()) {
      return false;
    }
    if (getVersion() != other.getVersion()) {
      return false;
    }
    if (!java.util.Arrays.equals(getChecksum(), other.getChecksum())) {
      return false;
    }
    final Object this$resourceName = getResourceName();
    final Object other$resourceName = other.getResourceName();
    if (this$resourceName == null
        ? other$resourceName != null
        : !this$resourceName.equals(other$resourceName)) {
      return false;
    }
    final Object this$bpmnProcessId = getBpmnProcessId();
    final Object other$bpmnProcessId = other.getBpmnProcessId();
    if (this$bpmnProcessId == null
        ? other$bpmnProcessId != null
        : !this$bpmnProcessId.equals(other$bpmnProcessId)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ZeebeProcessDefinitionDataDto(resource="
        + java.util.Arrays.toString(getResource())
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", version="
        + getVersion()
        + ", checksum="
        + java.util.Arrays.toString(getChecksum())
        + ", resourceName="
        + getResourceName()
        + ", bpmnProcessId="
        + getBpmnProcessId()
        + ", tenantId="
        + getTenantId()
        + ")";
  }
}
