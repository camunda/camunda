/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.Protocol;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class CompressedLongArrayValueTest {
  private final MsgPackWriter writer = new MsgPackWriter();
  private final MsgPackReader reader = new MsgPackReader();

  @Test
  void shouldWriteAndReadArray() {
    // given
    final CompressedLongArrayValue value = new CompressedLongArrayValue();
    value.setValues(new long[] {1, 2, 3, 4, 5});

    // when
    final int encodedLength = value.getEncodedLength();
    System.out.println(encodedLength);
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[encodedLength]);

    writer.wrap(buffer, 0);
    value.write(writer);

    reader.wrap(buffer, 0, encodedLength);

    final CompressedLongArrayValue result = new CompressedLongArrayValue();
    result.read(reader);

    // then
    assertArrayEquals(new long[] {1, 2, 3, 4, 5}, result.getValues());
  }

  @Test
  void shouldEncodeCompressed() {
    final long[] values =
        new long[] {
          Protocol.encodePartitionId(3, 1),
          Protocol.encodePartitionId(3, 1000),
          Protocol.encodePartitionId(3, 1234),
          Protocol.encodePartitionId(3, 1238),
          Protocol.encodePartitionId(3, 2127)
        };
    final int compressedLength = encodeCompressed(values);
    final int uncompressedLength = encodeUncompressed(values);

    System.out.println(compressedLength);
    System.out.println(uncompressedLength);

    assertTrue(compressedLength < uncompressedLength);
  }

  private int encodeCompressed(final long[] values) {
    final var value = new CompressedLongArrayValue();
    value.setValues(values);
    final var json = new StringBuilder();
    value.writeJSON(json);
    System.out.println(json.toString());
    return value.getEncodedLength();
  }

  private int encodeUncompressed(final long[] values) {
    final var value = new ArrayValue<>(LongValue::new);
    for (final var v : values) {
      value.add().setValue(v);
    }

    return value.getEncodedLength();
  }
}
