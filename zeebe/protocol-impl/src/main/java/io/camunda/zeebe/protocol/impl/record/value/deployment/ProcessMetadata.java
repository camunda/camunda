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
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * This class is used in the DeploymentRecord, only to send the process meta information back to the
 * user. It is similar to {@link ProcessRecord} except that it doesn't contain the actual resources.
 */
@MsgpackPojo
@NullMarked
public final class ProcessMetadata extends MsgpackBufferValue<ProcessMetadata>
    implements MsgpackRecordValue, ProcessMetadataValue {

  @Nullable DirectBuffer bpmnProcessId;
  final RequiredInt version = new RequiredInt();
  final RequiredLong processDefinitionKey = new RequiredLong();
  @Nullable DirectBuffer resourceName;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer checksum;

  // should be set to true if the process was already deployed - property should not be exported
  boolean isDuplicate = false;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  DirectBuffer versionTag = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(Decoding.required(bpmnProcessId, "bpmnProcessId"));
  }

  @Override
  public int getVersion() {
    return version.get();
  }

  public ProcessMetadata setVersion(final int version) {
    this.version.set(version);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  @Override
  public long getProcessDefinitionKey() {
    return getKey();
  }

  @Override
  public String getResourceName() {
    return bufferAsString(Decoding.required(resourceName, "resourceName"));
  }

  @Override
  public byte[] getChecksum() {
    return BufferUtil.bufferAsArray(Decoding.required(checksum, "checksum"));
  }

  public ProcessMetadata setChecksum(final DirectBuffer checksumBuffer) {
    checksum = new UnsafeBuffer(checksumBuffer, 0, checksumBuffer.capacity());
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicate;
  }

  public ProcessMetadata setDuplicate(final boolean isDuplicate) {
    this.isDuplicate = isDuplicate;
    return this;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public ProcessMetadata setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public ProcessMetadata setResourceName(final String resourceName) {
    this.resourceName = BufferUtil.wrapString(resourceName);
    return this;
  }

  public ProcessMetadata setResourceName(final DirectBuffer resourceName) {
    this.resourceName = new UnsafeBuffer(resourceName, 0, resourceName.capacity());
    return this;
  }

  public ProcessMetadata setVersionTag(final String versionTag) {
    this.versionTag = BufferUtil.wrapString(versionTag);
    return this;
  }

  public ProcessMetadata setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public ProcessMetadata setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = new UnsafeBuffer(bpmnProcessId, 0, bpmnProcessId.capacity());
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return Decoding.required(checksum, "checksum");
  }

  @JsonIgnore
  public long getKey() {
    return processDefinitionKey.get();
  }

  public ProcessMetadata setKey(final long key) {
    processDefinitionKey.set(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return Decoding.required(bpmnProcessId, "bpmnProcessId");
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return Decoding.required(resourceName, "resourceName");
  }

  public ProcessMetadata setBpmnProcessId(
      final DirectBuffer bpmnProcessId, final int offset, final int length) {
    this.bpmnProcessId = new UnsafeBuffer(bpmnProcessId, offset, length);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ProcessMetadata setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessMetadata> codec() {
    return ProcessMetadataCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessMetadata> ops() {
    return ProcessMetadataOps.INSTANCE;
  }
}
