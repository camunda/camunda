/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackScratch;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field names and their
 * order are the stored format.
 */
@MsgpackPojo
public final class IndexedRecord implements DbValue {

  long key;
  ProcessInstanceIntent state;

  @MsgpackProperty("processInstanceRecord")
  final ProcessInstanceRecord value = new ProcessInstanceRecord();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  IndexedRecord() {}

  public IndexedRecord(
      final long key,
      final ProcessInstanceIntent instanceState,
      final ProcessInstanceRecord record) {
    this();
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
    return state;
  }

  public IndexedRecord setState(final ProcessInstanceIntent state) {
    this.state = state;
    return this;
  }

  public boolean hasState(final ProcessInstanceIntent state) {
    return getState() == state;
  }

  public ProcessInstanceRecord getValue() {
    return value;
  }

  public IndexedRecord setValue(final ProcessInstanceRecord value) {
    final MutableDirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = value.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    value.write(valueBuffer, 0);
    this.value.wrap(valueBuffer, 0, encodedLength);

    return this;
  }

  // TODO: the DbValue contract below is kept as is until the column families stop requiring it.
  @Override
  public int getLength() {
    return IndexedRecordCodec.INSTANCE.getLength(this);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return IndexedRecordCodec.INSTANCE.write(MsgpackScratch.writer(buffer, offset), this);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    // the nested record references the buffer it is read from, so it needs its own copy
    final byte[] bytes = new byte[length];
    buffer.getBytes(offset, bytes, 0, length);
    IndexedRecordCodec.INSTANCE.decode(
        MsgpackScratch.reader(new UnsafeBuffer(bytes), 0, length), this);
  }
}
