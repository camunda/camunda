/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBusinessIdRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessInstanceBusinessIdRecord
    extends MsgpackBufferValue<ProcessInstanceBusinessIdRecord>
    implements MsgpackRecordValue, ProcessInstanceBusinessIdRecordValue {

  long processInstanceKey = -1L;
  DirectBuffer businessId = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  long processDefinitionKey = -1L;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public ProcessInstanceBusinessIdRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ProcessInstanceBusinessIdRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceBusinessIdRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public ProcessInstanceBusinessIdRecord setRootProcessInstanceKey(
      final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ProcessInstanceBusinessIdRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  public ProcessInstanceBusinessIdRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ProcessInstanceBusinessIdRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessInstanceBusinessIdRecord> codec() {
    return ProcessInstanceBusinessIdRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceBusinessIdRecord> ops() {
    return ProcessInstanceBusinessIdRecordOps.INSTANCE;
  }
}
