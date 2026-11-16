/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Lets the frozen XxxV1 copies of the engine state classes keep a record that is a generated {@link
 * MsgpackBufferValue} inside an {@code ObjectProperty}/{@code ArrayProperty}. It stores the record
 * as the embedded map, byte for byte as the record is written, which is what the former property
 * tree record did. Test only.
 */
public final class RecordObjectValue<R extends MsgpackBufferValue<R>> extends ObjectValue {

  private final R record;

  public RecordObjectValue(final R record) {
    super(0);
    this.record = record;
  }

  public R get() {
    return record;
  }

  @Override
  public void reset() {
    record.reset();
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append(record);
  }

  @Override
  public int write(final MsgPackWriter writer) {
    final var bytes = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(bytes, 0);
    return writer.writeRaw(bytes);
  }

  @Override
  public void read(final MsgPackReader reader) {
    final int start = reader.getOffset();
    reader.skipValue();
    record.wrap(reader.getBuffer(), start, reader.getOffset() - start);
  }

  @Override
  public int getEncodedLength() {
    return record.getLength();
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final RecordObjectValue<?> other && record.equals(other.record);
  }

  @Override
  public int hashCode() {
    return record.hashCode();
  }
}
