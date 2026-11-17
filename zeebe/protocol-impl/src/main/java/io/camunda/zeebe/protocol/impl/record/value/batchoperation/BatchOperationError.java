/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties("length")
public final class BatchOperationError extends MsgpackBufferValue<BatchOperationError>
    implements BatchOperationErrorValue {

  int partitionId = 0;
  BatchOperationErrorType type = BatchOperationErrorType.UNKNOWN;
  DirectBuffer message = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public BatchOperationError wrap(final BatchOperationErrorValue record) {
    setPartitionId(record.getPartitionId());
    setType(record.getType());
    setMessage(record.getMessage());
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  public BatchOperationError setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public BatchOperationErrorType getType() {
    return type;
  }

  public BatchOperationError setType(final BatchOperationErrorType type) {
    this.type = type;
    return this;
  }

  @Override
  public String getMessage() {
    return BufferUtil.bufferAsString(message);
  }

  public BatchOperationError setMessage(final String message) {
    this.message = BufferUtil.wrapString(message);
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
  protected MsgpackCodec<BatchOperationError> codec() {
    return BatchOperationErrorCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationError> ops() {
    return BatchOperationErrorOps.INSTANCE;
  }
}
