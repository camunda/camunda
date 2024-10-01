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
  private String versionTag;

  public ZeebeProcessDefinitionDataDto() {}

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
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

  @Override
  public String getTenantId() {
    return StringUtils.isEmpty(tenantId) ? ZEEBE_DEFAULT_TENANT_ID : tenantId;
  }

  public byte[] getResource() {
    return this.resource;
  }

  public long getProcessDefinitionKey() {
    return this.processDefinitionKey;
  }

  public int getVersion() {
    return this.version;
  }

  public byte[] getChecksum() {
    return this.checksum;
  }

  public String getResourceName() {
    return this.resourceName;
  }

  public String getBpmnProcessId() {
    return this.bpmnProcessId;
  }

  public String getVersionTag() {
    return this.versionTag;
  }

  public void setResource(byte[] resource) {
    this.resource = resource;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public void setChecksum(byte[] checksum) {
    this.checksum = checksum;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public void setVersionTag(String versionTag) {
    this.versionTag = versionTag;
  }

  public String toString() {
    return "ZeebeProcessDefinitionDataDto(resource="
        + java.util.Arrays.toString(this.getResource())
        + ", processDefinitionKey="
        + this.getProcessDefinitionKey()
        + ", version="
        + this.getVersion()
        + ", checksum="
        + java.util.Arrays.toString(this.getChecksum())
        + ", resourceName="
        + this.getResourceName()
        + ", bpmnProcessId="
        + this.getBpmnProcessId()
        + ", tenantId="
        + this.getTenantId()
        + ", versionTag="
        + this.getVersionTag()
        + ")";
  }

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
    if (!java.util.Arrays.equals(this.getResource(), other.getResource())) {
      return false;
    }
    if (this.getProcessDefinitionKey() != other.getProcessDefinitionKey()) {
      return false;
    }
    if (this.getVersion() != other.getVersion()) {
      return false;
    }
    if (!java.util.Arrays.equals(this.getChecksum(), other.getChecksum())) {
      return false;
    }
    final Object this$resourceName = this.getResourceName();
    final Object other$resourceName = other.getResourceName();
    if (this$resourceName == null
        ? other$resourceName != null
        : !this$resourceName.equals(other$resourceName)) {
      return false;
    }
    final Object this$bpmnProcessId = this.getBpmnProcessId();
    final Object other$bpmnProcessId = other.getBpmnProcessId();
    if (this$bpmnProcessId == null
        ? other$bpmnProcessId != null
        : !this$bpmnProcessId.equals(other$bpmnProcessId)) {
      return false;
    }
    final Object this$tenantId = this.getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$versionTag = this.getVersionTag();
    final Object other$versionTag = other.getVersionTag();
    if (this$versionTag == null
        ? other$versionTag != null
        : !this$versionTag.equals(other$versionTag)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeProcessDefinitionDataDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + java.util.Arrays.hashCode(this.getResource());
    final long $processDefinitionKey = this.getProcessDefinitionKey();
    result = result * PRIME + (int) ($processDefinitionKey >>> 32 ^ $processDefinitionKey);
    result = result * PRIME + this.getVersion();
    result = result * PRIME + java.util.Arrays.hashCode(this.getChecksum());
    final Object $resourceName = this.getResourceName();
    result = result * PRIME + ($resourceName == null ? 43 : $resourceName.hashCode());
    final Object $bpmnProcessId = this.getBpmnProcessId();
    result = result * PRIME + ($bpmnProcessId == null ? 43 : $bpmnProcessId.hashCode());
    final Object $tenantId = this.getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $versionTag = this.getVersionTag();
    result = result * PRIME + ($versionTag == null ? 43 : $versionTag.hashCode());
    return result;
  }
}
