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
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class BatchOperationLifecycleManagementRecord
    extends MsgpackBufferValue<BatchOperationLifecycleManagementRecord>
    implements MsgpackRecordValue, BatchOperationLifecycleManagementRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL = "storageOrdinal";

  final RequiredLong batchOperationKey = new RequiredLong();
  int storageOrdinal = 0;
  final List<BatchOperationError> errors = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getBatchOperationKey() {
    return batchOperationKey.get();
  }

  public BatchOperationLifecycleManagementRecord setBatchOperationKey(
      final Long batchOperationKey) {
    this.batchOperationKey.set(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public BatchOperationLifecycleManagementRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public BatchOperationLifecycleManagementRecord wrap(
      final BatchOperationLifecycleManagementRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinal(record.getStorageOrdinal());
    setErrors(record.getErrors().stream().map(BatchOperationError.class::cast).toList());
    return this;
  }

  @Override
  public List<BatchOperationErrorValue> getErrors() {
    return errors.stream().map(BatchOperationErrorValue.class::cast).toList();
  }

  public BatchOperationLifecycleManagementRecord setErrors(final List<BatchOperationError> errors) {
    this.errors.clear();
    for (final var error : errors) {
      this.errors.add(new BatchOperationError().wrap(error));
    }
    return this;
  }

  @Override
  protected MsgpackCodec<BatchOperationLifecycleManagementRecord> codec() {
    return BatchOperationLifecycleManagementRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationLifecycleManagementRecord> ops() {
    return BatchOperationLifecycleManagementRecordOps.INSTANCE;
  }
}
