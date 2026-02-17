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
import java.util.Arrays;
import java.util.Objects;

/**
 * A specialized version of {@link ArrayValue<LongValue>} that uses delta-encoding to reduce the
 * encoded size in cases where the difference between subsequent values is small.
 *
 * <p>For example, the array [4, 13, 15, 16, 20] is encoded as [4, 9, 2, 1, 4], where the first
 * value remains the same but the following values represent the difference to the previous value.
 *
 * <p>This encoding saves space in case that the values would usually require many bits to encode,
 * but the difference between subsequent values is small and can be encoded with fewer bits.
 *
 * <p>Values are not delta encoded in memory, only when {@link
 * DeltaEncodedLongArrayValue#write(MsgPackWriter) writing in MsgPack format} or, conversely, {@link
 * DeltaEncodedLongArrayValue#read(MsgPackReader) reading from MsgPack format}, delta encoding is
 * used.
 *
 * <p>{@link DeltaEncodedLongArrayValue#writeJSON(StringBuilder) Writing in JSON format} does not
 * use delta encoding to keep this optimization internal and not leak to external systems. JSON is
 * not space-efficient in any case, so we'd rather keep the delta-encoding internal as much as
 * possible.
 *
 * @implNote This implementation relies on {@link MsgPackWriter#writeInteger(long)} automatically
 *     picking the smallest possible encoding for the given deltas. By implementing this value type
 *     in terms of a plain MsgPack array and integer values, we keep the implementation simple and
 *     avoid the need to implement a custom encoding scheme. We lose a bit of efficiency by not
 *     specializing for a fixed encoding of delta values but gain in flexibility because this type
 *     can be used even in cases where delta encoding is not efficient because deltas are large
 *     enough to require full 64-bit encoding.
 *     <p>This encoding is a classic space-time trade-off: we save space by encoding the values as
 *     deltas, but pay in time because reading, writing and calculating encoded size requires long
 *     addition/subtraction and array lookbehind. We assume that this is easily optimized by any JVM
 *     and that the space savings are worth the trade-off.
 */
public final class DeltaEncodedLongArrayValue extends BaseValue {
  private long[] values;

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append("[");

    for (var i = 0; i < values.length; i++) {
      if (i > 0) {
        builder.append(",");
      }
      builder.append(values[i]);
    }

    builder.append("]");
  }

  @Override
  public int write(final MsgPackWriter writer) {
    int written = writer.writeArrayHeader(values.length);

    for (int i = 0; i < values.length; i++) {
      if (i == 0) {
        written += writer.writeInteger(values[i]);
      } else {
        written += writer.writeInteger(values[i] - values[i - 1]);
      }
    }
    return written;
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
    final int header = MsgPackWriter.getEncodedArrayHeaderLength(values.length);
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

  @Override
  public int hashCode() {
    return Objects.hashCode(values);
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof final DeltaEncodedLongArrayValue that)) {
      return false;
    }
    return Arrays.equals(values, that.values);
  }

  public long[] getValues() {
    return values;
  }

  public void setValues(final long[] values) {
    this.values = values;
  }
}
