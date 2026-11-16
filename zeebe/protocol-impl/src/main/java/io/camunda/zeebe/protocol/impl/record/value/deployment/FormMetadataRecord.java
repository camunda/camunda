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
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class FormMetadataRecord extends MsgpackBufferValue<FormMetadataRecord>
    implements MsgpackRecordValue, FormMetadataValue {

  @Nullable DirectBuffer formId;
  final RequiredInt version = new RequiredInt();
  final RequiredLong formKey = new RequiredLong();
  @Nullable DirectBuffer resourceName;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer checksum;

  boolean isDuplicate = false;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  DirectBuffer versionTag = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getFormId() {
    return bufferAsString(Decoding.required(formId, "formId"));
  }

  public FormMetadataRecord setFormId(final String formId) {
    this.formId = BufferUtil.wrapString(formId);
    return this;
  }

  @Override
  public int getVersion() {
    return version.get();
  }

  public FormMetadataRecord setVersion(final int formVersion) {
    version.set(formVersion);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  public FormMetadataRecord setVersionTag(final String versionTag) {
    this.versionTag = BufferUtil.wrapString(versionTag);
    return this;
  }

  @Override
  public long getFormKey() {
    return formKey.get();
  }

  public FormMetadataRecord setFormKey(final long formKey) {
    this.formKey.set(formKey);
    return this;
  }

  @Override
  public String getResourceName() {
    return bufferAsString(Decoding.required(resourceName, "resourceName"));
  }

  public FormMetadataRecord setResourceName(final String resourceName) {
    this.resourceName = BufferUtil.wrapString(resourceName);
    return this;
  }

  @Override
  public byte[] getChecksum() {
    return bufferAsArray(Decoding.required(checksum, "checksum"));
  }

  public FormMetadataRecord setChecksum(final DirectBuffer checksum) {
    this.checksum = new UnsafeBuffer(checksum, 0, checksum.capacity());
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicate;
  }

  public FormMetadataRecord setDuplicate(final boolean isDuplicate) {
    this.isDuplicate = isDuplicate;
    return this;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public FormMetadataRecord setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getFormIdBuffer() {
    return Decoding.required(formId, "formId");
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return Decoding.required(resourceName, "resourceName");
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return Decoding.required(checksum, "checksum");
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public FormMetadataRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<FormMetadataRecord> codec() {
    return FormMetadataRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<FormMetadataRecord> ops() {
    return FormMetadataRecordOps.INSTANCE;
  }
}
