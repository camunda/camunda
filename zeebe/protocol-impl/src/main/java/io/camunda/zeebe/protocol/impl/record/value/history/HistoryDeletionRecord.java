/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.history;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class HistoryDeletionRecord extends MsgpackBufferValue<HistoryDeletionRecord>
    implements MsgpackRecordValue, HistoryDeletionRecordValue {

  final RequiredLong resourceKey = new RequiredLong();
  @Nullable HistoryDeletionType resourceType;
  int resourceStorageOrdinal = 0;
  DirectBuffer processId = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  DirectBuffer decisionDefinitionId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getResourceKey() {
    return resourceKey.get();
  }

  public HistoryDeletionRecord setResourceKey(final long resourceKey) {
    this.resourceKey.set(resourceKey);
    return this;
  }

  @Override
  public HistoryDeletionType getResourceType() {
    return Decoding.required(resourceType, "resourceType");
  }

  public HistoryDeletionRecord setResourceType(final HistoryDeletionType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  @Override
  public int getResourceStorageOrdinal() {
    return resourceStorageOrdinal;
  }

  @Override
  public String getProcessId() {
    return BufferUtil.bufferAsString(processId);
  }

  public HistoryDeletionRecord setProcessId(final String processId) {
    this.processId = BufferUtil.wrapString(processId);
    return this;
  }

  @Override
  public String getDecisionDefinitionId() {
    return BufferUtil.bufferAsString(decisionDefinitionId);
  }

  public HistoryDeletionRecord setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = BufferUtil.wrapString(decisionDefinitionId);
    return this;
  }

  public HistoryDeletionRecord setResourceStorageOrdinal(final int resourceStorageOrdinal) {
    this.resourceStorageOrdinal = resourceStorageOrdinal;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public HistoryDeletionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<HistoryDeletionRecord> codec() {
    return HistoryDeletionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<HistoryDeletionRecord> ops() {
    return HistoryDeletionRecordOps.INSTANCE;
  }
}
