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
import io.camunda.zeebe.protocol.record.value.BatchOperationPartitionLifecycleRecordValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class BatchOperationPartitionLifecycleRecord
    extends MsgpackBufferValue<BatchOperationPartitionLifecycleRecord>
    implements MsgpackRecordValue, BatchOperationPartitionLifecycleRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL = "storageOrdinal";
  public static final String PROP_SOURCE_PARTITION_ID = "sourcePartitionId";

  final RequiredLong batchOperationKey = new RequiredLong();
  int storageOrdinal = 0;
  int sourcePartitionId = -1;
  final BatchOperationError error = new BatchOperationError();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getBatchOperationKey() {
    return batchOperationKey.get();
  }

  public BatchOperationPartitionLifecycleRecord setBatchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey.set(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public BatchOperationPartitionLifecycleRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public Integer getSourcePartitionId() {
    return sourcePartitionId;
  }

  public BatchOperationPartitionLifecycleRecord setSourcePartitionId(final int sourcePartitionId) {
    this.sourcePartitionId = sourcePartitionId;
    return this;
  }

  public BatchOperationError getError() {
    return error;
  }

  public BatchOperationPartitionLifecycleRecord setError(final BatchOperationError error) {
    this.error.wrap(error);
    return this;
  }

  public BatchOperationPartitionLifecycleRecord wrap(
      final BatchOperationPartitionLifecycleRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinal(record.getStorageOrdinal());
    setSourcePartitionId(record.getSourcePartitionId());
    setError(record.getError());
    return this;
  }

  @Override
  protected MsgpackCodec<BatchOperationPartitionLifecycleRecord> codec() {
    return BatchOperationPartitionLifecycleRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationPartitionLifecycleRecord> ops() {
    return BatchOperationPartitionLifecycleRecordOps.INSTANCE;
  }
}
