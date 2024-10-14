/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.record.value.deployment;

import io.camunda.zeebe.protocol.record.value.deployment.Form;
import java.util.Arrays;
import java.util.Objects;

public class FormRecordImpl implements Form {

  private byte[] resource;
  private String formId;
  private int version;
  private long formKey;
  private String resourceName;
  private byte[] checksum;
  private boolean duplicate;
  private String tenantId;
  private long deploymentKey;
  private String versionTag;

  @Override
  public byte[] getResource() {
    return resource;
  }

  public FormRecordImpl setResource(final byte[] resource) {
    this.resource = resource;
    return this;
  }

  @Override
  public String getFormId() {
    return formId;
  }

  public void setFormId(final String formId) {
    this.formId = formId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public void setVersion(final int version) {
    this.version = version;
  }

  @Override
  public String getVersionTag() {
    return versionTag;
  }

  public void setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
  }

  @Override
  public long getFormKey() {
    return formKey;
  }

  public void setFormKey(final long formKey) {
    this.formKey = formKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(final String resourceName) {
    this.resourceName = resourceName;
  }

  @Override
  public byte[] getChecksum() {
    return checksum;
  }

  public FormRecordImpl setChecksum(final byte[] checksum) {
    this.checksum = checksum;
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return duplicate;
  }

  public void setDuplicate(final boolean duplicate) {
    this.duplicate = duplicate;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public void setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        resource,
        formId,
        version,
        formKey,
        resourceName,
        checksum,
        duplicate,
        tenantId,
        deploymentKey,
        versionTag);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final FormRecordImpl that = (FormRecordImpl) o;
    return version == that.version
        && formKey == that.formKey
        && duplicate == that.duplicate
        && deploymentKey == that.deploymentKey
        && Objects.equals(resource, that.resource)
        && Objects.equals(formId, that.formId)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(checksum, that.checksum)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(versionTag, that.versionTag);
  }

  @Override
  public String toString() {
    return "FormRecordImpl{"
        + "resource="
        + Arrays.toString(resource)
        + ", formId='"
        + formId
        + '\''
        + ", version="
        + version
        + ", formKey="
        + formKey
        + ", resourceName='"
        + resourceName
        + '\''
        + ", checksum="
        + Arrays.toString(checksum)
        + ", duplicate="
        + duplicate
        + ", tenantId='"
        + tenantId
        + '\''
        + ", deploymentKey="
        + deploymentKey
        + ", versionTag='"
        + versionTag
        + "'} "
        + super.toString();
  }
}
