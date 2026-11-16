/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.db.DbValue;
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
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedForm extends MsgpackBufferValue<PersistedForm>
    implements DbValue, ProjectionOf<FormRecord, PersistedForm> {
  private static final long NO_DEPLOYMENT_KEY = -1L;

  @Nullable DirectBuffer formId;
  final RequiredInt version = new RequiredInt();
  final RequiredLong formKey = new RequiredLong();
  @Nullable DirectBuffer resourceName;

  @MsgpackProperty(binary = true)
  DirectBuffer resource = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  DirectBuffer checksum = Decoding.EMPTY;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = NO_DEPLOYMENT_KEY;
  DirectBuffer versionTag = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PersistedForm copy() {
    final var copy = new PersistedForm();
    copy.formId = BufferUtil.cloneBuffer(getFormId());
    copy.version.set(getVersion());
    copy.formKey.set(getFormKey());
    copy.resourceName = BufferUtil.cloneBuffer(getResourceName());
    copy.resource = BufferUtil.cloneBuffer(getResource());
    copy.checksum = BufferUtil.cloneBuffer(getChecksum());
    copy.tenantId = BufferUtil.wrapString(getTenantId());
    copy.deploymentKey = getDeploymentKey();
    copy.versionTag = BufferUtil.wrapString(getVersionTag());
    return copy;
  }

  public DirectBuffer getFormId() {
    return Decoding.required(formId, "formId");
  }

  public int getVersion() {
    return version.get();
  }

  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  public long getFormKey() {
    return formKey.get();
  }

  public DirectBuffer getResourceName() {
    return Decoding.required(resourceName, "resourceName");
  }

  public DirectBuffer getResource() {
    return resource;
  }

  public DirectBuffer getChecksum() {
    return checksum;
  }

  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public boolean hasDeploymentKey() {
    return deploymentKey != NO_DEPLOYMENT_KEY;
  }

  public long getDeploymentKey() {
    return deploymentKey;
  }

  public PersistedForm setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  @Override
  public PersistedForm wrap(final FormRecord record) {
    formId = BufferUtil.wrapString(record.getFormId());
    version.set(record.getVersion());
    formKey.set(record.getFormKey());
    resourceName = record.getResourceNameBuffer();
    resource = BufferUtil.wrapArray(record.getResource());
    checksum = record.getChecksumBuffer();
    tenantId = BufferUtil.wrapString(record.getTenantId());
    deploymentKey = record.getDeploymentKey();
    versionTag = BufferUtil.wrapString(record.getVersionTag());
    return this;
  }

  public boolean isDuplicateOf(final DirectBuffer resourceName, final DirectBuffer checksum) {
    return getResourceName().equals(resourceName) && getChecksum().equals(checksum);
  }

  @Override
  protected MsgpackCodec<PersistedForm> codec() {
    return PersistedFormCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedForm> ops() {
    return PersistedFormOps.INSTANCE;
  }
}
