/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;

public class CompressedLongArrayValue extends BaseValue {
  private long[] values;

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append("[");

    long currentValue = 0;
    for (int i = 0; i < values.length; i++) {
      if (i == 0) {
        currentValue = values[i];
        builder.append(values[i]);
      } else {
        builder.append(",");
        currentValue += values[i];
        builder.append(currentValue);
      }
    }

    builder.append("]");
  }

  @Override
  public void write(final MsgPackWriter writer) {
    writer.writeArrayHeader(values.length);

    for (int i = 0; i < values.length; i++) {
      if (i == 0) {
        writer.writeInteger(values[i]);
      } else {
        writer.writeInteger(values[i] - values[i - 1]);
      }
    }
  }

  @Override
  public void read(final MsgPackReader reader) {
    final int length = reader.readArrayHeader();

    values = new long[length];

    for (int i = 0; i < length; i++) {
      if (i == 0) {
        values[i] = reader.readInteger();
      } else {
        values[i] = values[i - 1] + reader.readInteger();
      }
    }
  }

  @Override
  public int getEncodedLength() {
    final int header = MsgPackWriter.getEncodedArrayHeaderLenght(values.length);
    int data = 0;
    for (int i = 0; i < values.length; i++) {
      if (i == 0) {
        data += MsgPackWriter.getEncodedLongValueLength(values[i]);
      } else {
        data += MsgPackWriter.getEncodedLongValueLength(values[i] - values[i - 1]);
      }
    }

    return header + data;
  }

  @Override
  public void reset() {
    values = null;
  }

  public long[] getValues() {
    return values;
  }

  void setValues(final long[] values) {
    this.values = values;
  }
}
