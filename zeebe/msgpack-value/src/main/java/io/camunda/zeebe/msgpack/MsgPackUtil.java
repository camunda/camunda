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

import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackToken;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
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

  /**
   * Deep-merges two MsgPack map documents.
   *
   * <p>Entries from {@code base} are included first, then {@code overrides} are applied. If both
   * documents contain a map for the same key, the maps are merged recursively. Otherwise, the
   * override value replaces the base.
   *
   * <p>Keys are compared using {@link ByteBuffer}, which uses content-based equality (unlike {@link
   * UnsafeBuffer}, which uses reference identity).
   */
  public static DirectBuffer mergeMsgPackDocuments(
      final DirectBuffer base, final DirectBuffer overrides) {
    final Map<ByteBuffer, DirectBuffer> baseEntries = new LinkedHashMap<>();
    readMsgPackMapEntries(base, baseEntries);

    final Map<ByteBuffer, DirectBuffer> overrideEntries = new LinkedHashMap<>();
    readMsgPackMapEntries(overrides, overrideEntries);

    // Deep merge: start with base, apply overrides
    final Map<ByteBuffer, DirectBuffer> merged = new LinkedHashMap<>(baseEntries);
    for (final var entry : overrideEntries.entrySet()) {
      final ByteBuffer key = entry.getKey();
      final DirectBuffer overrideValue = entry.getValue();
      final DirectBuffer baseValue = merged.get(key);

      if (baseValue != null && isMsgPackMap(baseValue) && isMsgPackMap(overrideValue)) {
        // Both values are maps — recursively deep-merge them
        merged.put(key, mergeMsgPackDocuments(baseValue, overrideValue));
      } else {
        // Override replaces base (or key only exists in override)
        merged.put(key, overrideValue);
      }
    }

    return writeMsgPackMap(merged);
  }

  /** Checks whether the given MsgPack-encoded value starts with a map header. */
  private static boolean isMsgPackMap(final DirectBuffer value) {
    if (value.capacity() == 0) {
      return false;
    }
    final byte header = value.getByte(0);
    return MsgPackCodes.isFixedMap(header)
        || header == MsgPackCodes.MAP16
        || header == MsgPackCodes.MAP32;
  }

  /**
   * Writes a map of raw MsgPack key-value pairs into a single MsgPack map document. Keys are stored
   * as {@link ByteBuffer} for reliable content-based equality; each key's backing byte array is
   * written directly as raw MsgPack bytes.
   */
  private static DirectBuffer writeMsgPackMap(final Map<ByteBuffer, DirectBuffer> entries) {
    final var resultBuffer = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter();
    writer.wrap(resultBuffer, 0);

    writer.writeMapHeader(entries.size());
    for (final var entry : entries.entrySet()) {
      final ByteBuffer key = entry.getKey();
      writer.writeRaw(new UnsafeBuffer(key.array(), key.arrayOffset(), key.remaining()));
      writer.writeRaw(entry.getValue());
    }

    return new UnsafeBuffer(resultBuffer, 0, writer.getOffset());
  }

  /**
   * Reads all key-value entries from a MsgPack map document and puts them into the given map. Each
   * key is extracted as a {@link ByteBuffer} (content-based equality) and each value as a cloned
   * {@link DirectBuffer} containing the raw MsgPack-encoded bytes. If a key already exists in the
   * map it is overwritten.
   */
  private static void readMsgPackMapEntries(
      final DirectBuffer document, final Map<ByteBuffer, DirectBuffer> entries) {
    final var reader = new MsgPackReader();
    reader.wrap(document, 0, document.capacity());

    final int mapSize = reader.readMapHeader();
    for (int i = 0; i < mapSize; i++) {
      final int keyStart = reader.getOffset();
      reader.skipValue();
      final int keyEnd = reader.getOffset();
      final var keyBytes = new byte[keyEnd - keyStart];
      document.getBytes(keyStart, keyBytes, 0, keyEnd - keyStart);

      final int valueStart = reader.getOffset();
      reader.skipValue();
      final int valueEnd = reader.getOffset();
      final var valueBuffer = new UnsafeBuffer(new byte[valueEnd - valueStart]);
      document.getBytes(valueStart, valueBuffer, 0, valueEnd - valueStart);

      entries.put(ByteBuffer.wrap(keyBytes), valueBuffer);
    }
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
