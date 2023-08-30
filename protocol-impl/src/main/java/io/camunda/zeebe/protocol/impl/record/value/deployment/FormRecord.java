/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class FormRecord extends UnifiedRecordValue implements Form {
  private final StringProperty formIdProp = new StringProperty("formId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty formKeyProp = new LongProperty("formKey");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum", new UnsafeBuffer());
  private final BinaryProperty resourceProp = new BinaryProperty("resource", new UnsafeBuffer());

  public FormRecord() {
    declareProperty(formIdProp)
        .declareProperty(versionProp)
        .declareProperty(formKeyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(resourceProp);
  }

  public FormRecord wrap(final FormMetadataRecord metadata, final byte[] resource) {
    formIdProp.setValue(metadata.getFormId());
    versionProp.setValue(metadata.getVersion());
    checksumProp.setValue(metadata.getChecksumBuffer());
    formKeyProp.setValue(metadata.getFormKey());
    resourceNameProp.setValue(metadata.getResourceNameBuffer());
    resourceProp.setValue(BufferUtil.wrapArray(resource));
    return this;
  }

  @Override
  public String getFormId() {
    return BufferUtil.bufferAsString(formIdProp.getValue());
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  @Override
  public long getFormKey() {
    return formKeyProp.getValue();
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(resourceNameProp.getValue());
  }

  @Override
  public byte[] getChecksum() {
    return BufferUtil.bufferAsArray(checksumProp.getValue());
  }

  @Override
  public boolean isDuplicate() {
    return false;
  }

  public FormRecord setChecksum(final DirectBuffer checksumBuffer) {
    checksumProp.setValue(checksumBuffer);
    return this;
  }

  public FormRecord setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public FormRecord setResourceName(final DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public FormRecord setFormKey(final long key) {
    formKeyProp.setValue(key);
    return this;
  }

  public FormRecord setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  public FormRecord setFormId(final String formId) {
    formIdProp.setValue(formId);
    return this;
  }

  public FormRecord setFormId(final DirectBuffer formId) {
    formIdProp.setValue(formId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @Override
  public byte[] getResource() {
    return BufferUtil.bufferAsArray(resourceProp.getValue());
  }

  public FormRecord setResource(final DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return formIdProp.getValue();
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  public FormRecord setFormId(final DirectBuffer formId, final int offset, final int length) {
    formIdProp.setValue(formId, offset, length);
    return this;
  }

  public FormRecord setResource(final DirectBuffer resource, final int offset, final int length) {
    resourceProp.setValue(resource, offset, length);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getResourceBuffer() {
    return resourceProp.getValue();
  }

  @Override
  public String getTenantId() {
    // todo(#13320): replace dummy implementation
    return "";
  }
}
