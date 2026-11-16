/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class FormRecord extends MsgpackBufferValue<FormRecord>
    implements MsgpackRecordValue, Form {

  @Nullable DirectBuffer formId;
  final RequiredInt version = new RequiredInt();
  final RequiredLong formKey = new RequiredLong();
  @Nullable DirectBuffer resourceName;

  @MsgpackProperty(binary = true)
  DirectBuffer checksum = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  DirectBuffer resource = Decoding.EMPTY;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  DirectBuffer versionTag = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public FormRecord wrap(final FormMetadataRecord metadata, final byte[] resource) {
    formId = BufferUtil.wrapString(metadata.getFormId());
    version.set(metadata.getVersion());
    checksum = metadata.getChecksumBuffer();
    formKey.set(metadata.getFormKey());
    resourceName = metadata.getResourceNameBuffer();
    this.resource = BufferUtil.wrapArray(resource);
    tenantId = BufferUtil.wrapString(metadata.getTenantId());
    deploymentKey = metadata.getDeploymentKey();
    versionTag = BufferUtil.wrapString(metadata.getVersionTag());
    return this;
  }

  @Override
  public String getFormId() {
    return bufferAsString(Decoding.required(formId, "formId"));
  }

  public FormRecord setFormId(final String formId) {
    this.formId = BufferUtil.wrapString(formId);
    return this;
  }

  public FormRecord setFormId(final DirectBuffer formId) {
    this.formId = new UnsafeBuffer(formId, 0, formId.capacity());
    return this;
  }

  @Override
  public int getVersion() {
    return version.get();
  }

  public FormRecord setVersion(final int version) {
    this.version.set(version);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  public FormRecord setVersionTag(final String versionTag) {
    this.versionTag = BufferUtil.wrapString(versionTag);
    return this;
  }

  @Override
  public long getFormKey() {
    return formKey.get();
  }

  public FormRecord setFormKey(final long key) {
    formKey.set(key);
    return this;
  }

  @Override
  public String getResourceName() {
    return bufferAsString(Decoding.required(resourceName, "resourceName"));
  }

  public FormRecord setResourceName(final String resourceName) {
    this.resourceName = BufferUtil.wrapString(resourceName);
    return this;
  }

  public FormRecord setResourceName(final DirectBuffer resourceName) {
    this.resourceName = new UnsafeBuffer(resourceName, 0, resourceName.capacity());
    return this;
  }

  @Override
  public byte[] getChecksum() {
    return BufferUtil.bufferAsArray(checksum);
  }

  public FormRecord setChecksum(final DirectBuffer checksumBuffer) {
    checksum = new UnsafeBuffer(checksumBuffer, 0, checksumBuffer.capacity());
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return false;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public FormRecord setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksum;
  }

  @Override
  public byte[] getResource() {
    return BufferUtil.bufferAsArray(resource);
  }

  public FormRecord setResource(final DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  @JsonIgnore
  public DirectBuffer getFormIdBuffer() {
    return Decoding.required(formId, "formId");
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return Decoding.required(resourceName, "resourceName");
  }

  public FormRecord setFormId(final DirectBuffer formId, final int offset, final int length) {
    this.formId = new UnsafeBuffer(formId, offset, length);
    return this;
  }

  public FormRecord setResource(final DirectBuffer resource, final int offset, final int length) {
    this.resource = new UnsafeBuffer(resource, offset, length);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getResourceBuffer() {
    return resource;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public FormRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<FormRecord> codec() {
    return FormRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<FormRecord> ops() {
    return FormRecordOps.INSTANCE;
  }
}
