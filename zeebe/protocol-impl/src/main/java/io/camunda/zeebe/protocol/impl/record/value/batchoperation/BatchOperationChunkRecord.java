/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class BatchOperationChunkRecord extends MsgpackBufferValue<BatchOperationChunkRecord>
    implements MsgpackRecordValue, BatchOperationChunkRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL = "storageOrdinal";
  public static final String PROP_ITEMS_LIST = "items";

  final RequiredLong batchOperationKey = new RequiredLong();
  int storageOrdinal = 0;
  final List<BatchOperationItem> items = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getBatchOperationKey() {
    return batchOperationKey.get();
  }

  public BatchOperationChunkRecord setBatchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey.set(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public BatchOperationChunkRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public List<BatchOperationItemValue> getItems() {
    return items.stream().collect(Collectors.toList());
  }

  public BatchOperationChunkRecord setItems(final Collection<BatchOperationItemValue> items) {
    this.items.clear();
    items.forEach(item -> this.items.add(new BatchOperationItem().wrap(item)));
    return this;
  }

  public void wrap(final BatchOperationChunkRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinal(record.getStorageOrdinal());
    setItems(record.getItems());
  }

  @Override
  protected MsgpackCodec<BatchOperationChunkRecord> codec() {
    return BatchOperationChunkRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationChunkRecord> ops() {
    return BatchOperationChunkRecordOps.INSTANCE;
  }
}
