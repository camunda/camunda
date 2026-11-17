/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.AsyncRequestRecordValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AsyncRequestRecord extends MsgpackBufferValue<AsyncRequestRecord>
    implements MsgpackRecordValue, AsyncRequestRecordValue {

  long scopeKey = -1L;
  ValueType valueType = ValueType.NULL_VAL;
  int intent = Intent.NULL_VAL;
  long requestId = -1L;
  int requestStreamId = -1;
  long operationReference = -1L;
  long batchOperationReference = -1L;
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final AsyncRequestRecord record) {
    scopeKey = record.getScopeKey();
    valueType = record.getValueType();
    intent = record.getIntent().value();
    requestId = record.getRequestId();
    requestStreamId = record.getRequestStreamId();
    operationReference = record.getOperationReference();
    batchOperationReference = record.getBatchOperationReference();
    storageOrdinal = record.getStorageOrdinal();
  }

  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  public AsyncRequestRecord setScopeKey(final long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  @Override
  public ValueType getValueType() {
    return valueType;
  }

  public AsyncRequestRecord setValueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  @Override
  public Intent getIntent() {
    return getIntent(intent);
  }

  public AsyncRequestRecord setIntent(final Intent intent) {
    this.intent = intent.value();
    return this;
  }

  private Intent getIntent(final int intentValue) {
    if (intentValue < 0 || intentValue > Short.MAX_VALUE) {
      throw new IllegalStateException(
          String.format(
              "Expected to read the intent, but it's persisted value '%d' is not a short integer",
              intentValue));
    }
    return Intent.fromProtocolValue(getValueType(), (short) intentValue);
  }

  @Override
  public long getRequestId() {
    return requestId;
  }

  public AsyncRequestRecord setRequestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }

  @Override
  public int getRequestStreamId() {
    return requestStreamId;
  }

  public AsyncRequestRecord setRequestStreamId(final int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  @Override
  public long getOperationReference() {
    return operationReference;
  }

  public AsyncRequestRecord setOperationReference(final long operationReference) {
    this.operationReference = operationReference;
    return this;
  }

  @Override
  public long getBatchOperationReference() {
    return batchOperationReference;
  }

  public AsyncRequestRecord setBatchOperationReference(final long batchOperationReference) {
    this.batchOperationReference = batchOperationReference;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public AsyncRequestRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<AsyncRequestRecord> codec() {
    return AsyncRequestRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AsyncRequestRecord> ops() {
    return AsyncRequestRecordOps.INSTANCE;
  }
}
