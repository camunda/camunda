/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class IndexedRecord extends MsgpackBufferValue<IndexedRecord> implements DbValue {

  long key = 0L;
  @Nullable ProcessInstanceIntent state;
  final ProcessInstanceRecord processInstanceRecord = new ProcessInstanceRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  IndexedRecord() {}

  public IndexedRecord(
      final long key,
      final ProcessInstanceIntent instanceState,
      final ProcessInstanceRecord record) {
    this.key = key;
    state = instanceState;
    setValue(record);
  }

  public long getKey() {
    return key;
  }

  public IndexedRecord setKey(final long key) {
    this.key = key;
    return this;
  }

  public ProcessInstanceIntent getState() {
    return Decoding.required(state, "state");
  }

  public IndexedRecord setState(final ProcessInstanceIntent state) {
    this.state = state;
    return this;
  }

  public boolean hasState(final ProcessInstanceIntent state) {
    return getState() == state;
  }

  public ProcessInstanceRecord getValue() {
    return processInstanceRecord;
  }

  public IndexedRecord setValue(final ProcessInstanceRecord value) {
    final MutableDirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = value.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    value.write(valueBuffer, 0);
    processInstanceRecord.wrap(valueBuffer, 0, encodedLength);

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
  protected MsgpackCodec<IndexedRecord> codec() {
    return IndexedRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<IndexedRecord> ops() {
    return IndexedRecordOps.INSTANCE;
  }
}
