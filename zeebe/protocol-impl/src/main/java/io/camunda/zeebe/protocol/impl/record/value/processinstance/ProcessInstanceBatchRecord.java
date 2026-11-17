/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessInstanceBatchRecord extends MsgpackBufferValue<ProcessInstanceBatchRecord>
    implements MsgpackRecordValue, ProcessInstanceBatchRecordValue {

  final RequiredLong processInstanceKey = new RequiredLong();
  long processDefinitionKey = -1L;
  final RequiredLong batchElementInstanceKey = new RequiredLong();

  /**
   * The index is used to keep track of the position in the batch. When the index is -1, there won't
   * be another batch.
   *
   * <p>Depending on the Intent the index is used differently:
   *
   * <ul>
   *   <li>TERMINATE - The index is the element instance key of the first child instance of the next
   *       batch.
   *   <li>ACTIVATE - The index is a counter, indicating how many more child instances need to be
   *       activated.
   * </ul>
   */
  long index = -1L;

  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey.get();
  }

  public ProcessInstanceBatchRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey.set(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceBatchRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public long getBatchElementInstanceKey() {
    return batchElementInstanceKey.get();
  }

  public ProcessInstanceBatchRecord setBatchElementInstanceKey(final long batchElementInstanceKey) {
    this.batchElementInstanceKey.set(batchElementInstanceKey);
    return this;
  }

  @Override
  public long getIndex() {
    return index;
  }

  public ProcessInstanceBatchRecord setIndex(final long index) {
    this.index = index;
    return this;
  }

  @Override
  public String getTenantId() {
    // todo(#13774): replace dummy implementation
    return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ProcessInstanceBatchRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessInstanceBatchRecord> codec() {
    return ProcessInstanceBatchRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceBatchRecord> ops() {
    return ProcessInstanceBatchRecordOps.INSTANCE;
  }
}
