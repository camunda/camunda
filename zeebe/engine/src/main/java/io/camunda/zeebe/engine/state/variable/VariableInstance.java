/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field names and their
 * order are the stored format.
 */
@MsgpackPojo
public final class VariableInstance extends MsgpackBufferValue<VariableInstance>
    implements DbValue {

  long key;

  @MsgpackProperty(binary = true)
  DirectBuffer value = Decoding.EMPTY;

  // the metadata record is not migrated yet, so it is decoded by reference to the input buffer
  final VariableMetadataRecord metadata = new VariableMetadataRecord();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public VariableInstance() {}

  public VariableMetadataRecord getMetadata() {
    return metadata;
  }

  public VariableInstance setMetadata(final VariableMetadataRecord metadata) {
    this.metadata.wrap(metadata);
    return this;
  }

  public long getKey() {
    return key;
  }

  public VariableInstance setKey(final long key) {
    this.key = key;
    return this;
  }

  public VariableInstance setValue(final DirectBuffer value, final int offset, final int length) {
    this.value = BufferUtil.cloneBuffer(value, offset, length);
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    // the nested metadata references the buffer it is read from, so it needs its own copy
    final byte[] bytes = new byte[length];
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(new UnsafeBuffer(bytes), 0, length);
  }
}
