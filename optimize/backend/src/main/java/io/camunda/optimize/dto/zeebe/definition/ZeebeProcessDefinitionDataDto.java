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
import java.util.Arrays;
import java.util.Objects;
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

  public void setResource(final byte[] resource) {
    this.resource = resource;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setVersion(final int version) {
    this.version = version;
  }

  public void setChecksum(final byte[] checksum) {
    this.checksum = checksum;
  }

  public void setResourceName(final String resourceName) {
    this.resourceName = resourceName;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public void setVersionTag(final String versionTag) {
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

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeProcessDefinitionDataDto that = (ZeebeProcessDefinitionDataDto) o;
    return processDefinitionKey == that.processDefinitionKey
        && version == that.version
        && Objects.deepEquals(resource, that.resource)
        && Objects.deepEquals(checksum, that.checksum)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(versionTag, that.versionTag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        Arrays.hashCode(resource),
        processDefinitionKey,
        version,
        Arrays.hashCode(checksum),
        resourceName,
        bpmnProcessId,
        tenantId,
        versionTag);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeProcessDefinitionDataDto;
  }
}
