/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.asyncrequest;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.collection.Tuple;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AsyncRequestMetadataValue extends MsgpackBufferValue<AsyncRequestMetadataValue>
    implements DbValue, ProjectionOf<Tuple<AsyncRequestRecord, Long>, AsyncRequestMetadataValue> {

  long asyncRequestKey = -1;
  final AsyncRequestRecord asyncRequestRecord = new AsyncRequestRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final long asyncRequestKey, final AsyncRequestRecord record) {
    this.asyncRequestKey = asyncRequestKey;
    asyncRequestRecord.wrap(record);
  }

  public AsyncRequestRecord getRecord() {
    return asyncRequestRecord;
  }

  public long getAsyncRequestKey() {
    return asyncRequestKey;
  }

  @Override
  public AsyncRequestMetadataValue wrap(final Tuple<AsyncRequestRecord, Long> source) {
    wrap(source.getRight(), source.getLeft());
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bytes = new byte[length];
    final UnsafeBuffer mutableBuffer = new UnsafeBuffer(bytes);
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(mutableBuffer, 0, length);
  }

  @Override
  protected MsgpackCodec<AsyncRequestMetadataValue> codec() {
    return AsyncRequestMetadataValueCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AsyncRequestMetadataValue> ops() {
    return AsyncRequestMetadataValueOps.INSTANCE;
  }
}
