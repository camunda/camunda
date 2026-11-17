/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties("length")
public final class BatchOperationItem extends MsgpackBufferValue<BatchOperationItem>
    implements BatchOperationItemValue {

  long itemKey = -1;
  long processInstanceKey = -1;
  long rootProcessInstanceKey = -1;
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public BatchOperationItem() {}

  public BatchOperationItem(
      final long itemKey, final long processInstanceKey, final long rootProcessInstanceKey) {
    this();
    setItemKey(itemKey);
    setProcessInstanceKey(processInstanceKey);
    setRootProcessInstanceKey(rootProcessInstanceKey);
  }

  public BatchOperationItem(
      final long itemKey,
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final int storageOrdinal) {
    this(itemKey, processInstanceKey, rootProcessInstanceKey);
    setStorageOrdinal(storageOrdinal);
  }

  @Override
  public long getItemKey() {
    return itemKey;
  }

  public BatchOperationItem setItemKey(final long itemKey) {
    this.itemKey = itemKey;
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public BatchOperationItem setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public BatchOperationItem setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public BatchOperationItem setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public BatchOperationItem wrap(final BatchOperationItemValue value) {
    setItemKey(value.getItemKey());
    setProcessInstanceKey(value.getProcessInstanceKey());
    setRootProcessInstanceKey(value.getRootProcessInstanceKey());
    setStorageOrdinal(value.getStorageOrdinal());
    return this;
  }

  /** Kept so that the exported JSON stays unchanged. */
  public boolean isEmpty() {
    return false;
  }

  /** Kept so that the exported JSON stays unchanged. */
  public int getEncodedLength() {
    return getLength();
  }

  @Override
  protected MsgpackCodec<BatchOperationItem> codec() {
    return BatchOperationItemCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationItem> ops() {
    return BatchOperationItemOps.INSTANCE;
  }
}
