/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessDefinitionRestoreRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessDefinitionRestoreRecord
    extends MsgpackBufferValue<ProcessDefinitionRestoreRecord>
    implements MsgpackRecordValue, ProcessDefinitionRestoreRecordValue {

  long processDefinitionKey = -1L;
  DirectBuffer tenantId = Decoding.EMPTY;
  int restoredCount = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessDefinitionRestoreRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ProcessDefinitionRestoreRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getRestoredCount() {
    return restoredCount;
  }

  public ProcessDefinitionRestoreRecord setRestoredCount(final int restoredCount) {
    this.restoredCount = restoredCount;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessDefinitionRestoreRecord> codec() {
    return ProcessDefinitionRestoreRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessDefinitionRestoreRecord> ops() {
    return ProcessDefinitionRestoreRecordOps.INSTANCE;
  }
}
