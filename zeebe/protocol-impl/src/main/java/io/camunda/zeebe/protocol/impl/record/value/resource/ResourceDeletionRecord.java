/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.resource;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ResourceDeletionRecord extends MsgpackBufferValue<ResourceDeletionRecord>
    implements MsgpackRecordValue, ResourceDeletionRecordValue {

  final RequiredLong resourceKey = new RequiredLong();
  DirectBuffer tenantId = Decoding.EMPTY;
  boolean deleteHistory = false;
  long batchOperationKey = -1L;
  int storageOrdinal = 0;
  BatchOperationType batchOperationType = BatchOperationType.DELETE_PROCESS_INSTANCE;
  ResourceType resourceType = ResourceType.UNKNOWN;
  DirectBuffer resourceId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getResourceKey() {
    return resourceKey.get();
  }

  public ResourceDeletionRecord setResourceKey(final long resourceKey) {
    this.resourceKey.set(resourceKey);
    return this;
  }

  @Override
  public boolean isDeleteHistory() {
    return deleteHistory;
  }

  public ResourceDeletionRecord setDeleteHistory(final boolean deleteHistory) {
    this.deleteHistory = deleteHistory;
    return this;
  }

  @Override
  public BatchOperationType getBatchOperationType() {
    return batchOperationType;
  }

  public ResourceDeletionRecord setBatchOperationType(final BatchOperationType batchOperationType) {
    this.batchOperationType = batchOperationType;
    return this;
  }

  @Override
  public ResourceType getResourceType() {
    return resourceType;
  }

  public ResourceDeletionRecord setResourceType(final ResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  @Override
  public String getResourceId() {
    return BufferUtil.bufferAsString(resourceId);
  }

  public ResourceDeletionRecord setResourceId(final String resourceId) {
    this.resourceId = BufferUtil.wrapString(resourceId);
    return this;
  }

  public ResourceDeletionRecord setResourceId(final DirectBuffer resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ResourceDeletionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKey;
  }

  public ResourceDeletionRecord setBatchOperationKey(final long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ResourceDeletionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ResourceDeletionRecord> codec() {
    return ResourceDeletionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ResourceDeletionRecord> ops() {
    return ResourceDeletionRecordOps.INSTANCE;
  }
}
