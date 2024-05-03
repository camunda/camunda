/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackToken;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MsgPackUtil {

  public static MutableDirectBuffer encodeMsgPack(final Consumer<MsgPackWriter> arg) {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 4]);
    encodeMsgPack(buffer, arg);
    return buffer;
  }

  private static void encodeMsgPack(
      final MutableDirectBuffer buffer, final Consumer<MsgPackWriter> arg) {
    final MsgPackWriter writer = new MsgPackWriter();
    writer.wrap(buffer, 0);
    arg.accept(writer);
    buffer.wrap(buffer, 0, writer.getOffset());
  }

  public static Map<String, Object> asMap(final byte[] array) {
    return asMap(wrapArray(array));
  }

  public static Map<String, Object> asMap(final DirectBuffer buffer) {
    return asMap(buffer, 0, buffer.capacity());
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> asMap(
      final DirectBuffer buffer, final int offset, final int length) {
    final MsgPackReader reader = new MsgPackReader();
    reader.wrap(buffer, offset, length);
    return (Map<String, Object>) deserializeElement(reader);
  }

  private static Object deserializeElement(final MsgPackReader reader) {

    final MsgPackToken token = reader.readToken();
    switch (token.getType()) {
      case INTEGER:
        return token.getIntegerValue();
      case FLOAT:
        return token.getFloatValue();
      case STRING:
        return bufferAsString(token.getValueBuffer());
      case BOOLEAN:
        return token.getBooleanValue();
      case BINARY:
        final DirectBuffer valueBuffer = token.getValueBuffer();
        final byte[] valueArray = new byte[valueBuffer.capacity()];
        valueBuffer.getBytes(0, valueArray);
        return valueArray;
      case MAP:
        final Map<String, Object> valueMap = new HashMap<>();
        final int mapSize = token.getSize();
        for (int i = 0; i < mapSize; i++) {
          final String key = (String) deserializeElement(reader);
          final Object value = deserializeElement(reader);
          valueMap.put(key, value);
        }
        return valueMap;
      case ARRAY:
        final int size = token.getSize();
        final Object[] arr = new Object[size];
        for (int i = 0; i < size; i++) {
          arr[i] = deserializeElement(reader);
        }
        return toString(arr);
      default:
        throw new RuntimeException("Not implemented yet");
    }
  }

  private static String toString(final Object[] arr) {
    final StringBuilder buf = new StringBuilder("[");

    if (arr.length > 0) {
      buf.append(arr[0].toString());
      for (int i = 1; i < arr.length; i++) {
        buf.append(", ");
        buf.append(arr[i].toString());
      }
    }

    buf.append("]");

    return buf.toString();
  }
}
