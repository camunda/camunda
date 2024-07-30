/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import org.agrona.DirectBuffer;

public class FormMetadataRecord extends UnifiedRecordValue implements FormMetadataValue {

  private final StringProperty formIdProp = new StringProperty("formId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty formKeyProp = new LongProperty("formKey");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum");
  private final BooleanProperty isDuplicateProp = new BooleanProperty("isDuplicate", false);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public FormMetadataRecord() {
    super(7);
    declareProperty(formIdProp)
        .declareProperty(versionProp)
        .declareProperty(formKeyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(isDuplicateProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getFormId() {
    return bufferAsString(formIdProp.getValue());
  }

  public FormMetadataRecord setFormId(final String formId) {
    formIdProp.setValue(formId);
    return this;
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  public FormMetadataRecord setVersion(final int formVersion) {
    versionProp.setValue(formVersion);
    return this;
  }

  @Override
  public long getFormKey() {
    return formKeyProp.getValue();
  }

  public FormMetadataRecord setFormKey(final long formKey) {
    formKeyProp.setValue(formKey);
    return this;
  }

  @Override
  public String getResourceName() {
    return bufferAsString(resourceNameProp.getValue());
  }

  public FormMetadataRecord setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  @Override
  public byte[] getChecksum() {
    return bufferAsArray(checksumProp.getValue());
  }

  public FormMetadataRecord setChecksum(final DirectBuffer checksum) {
    checksumProp.setValue(checksum);
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicateProp.getValue();
  }

  public FormMetadataRecord setDuplicate(final boolean isDuplicate) {
    isDuplicateProp.setValue(isDuplicate);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getFormIdBuffer() {
    return formIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public FormMetadataRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
