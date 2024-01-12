/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v850.record.value.deployment;

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

  @Override
  public byte[] getResource() {
    return resource;
  }

  public FormRecordImpl setResource(byte[] resource) {
    this.resource = resource;
    return this;
  }

  @Override
  public String getFormId() {
    return formId;
  }

  public void setFormId(String formId) {
    this.formId = formId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  @Override
  public long getFormKey() {
    return formKey;
  }

  public void setFormKey(long formKey) {
    this.formKey = formKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  @Override
  public byte[] getChecksum() {
    return checksum;
  }

  public FormRecordImpl setChecksum(byte[] checksum) {
    this.checksum = checksum;
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return duplicate;
  }

  public void setDuplicate(boolean duplicate) {
    this.duplicate = duplicate;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public boolean equals(Object o) {
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
        && Objects.equals(resource, that.resource)
        && Objects.equals(formId, that.formId)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(checksum, that.checksum)
        && Objects.equals(tenantId, that.tenantId);
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
        tenantId);
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
        + "} "
        + super.toString();
  }
}
