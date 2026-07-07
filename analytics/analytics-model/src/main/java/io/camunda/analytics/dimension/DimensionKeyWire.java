/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The canonical wire form of a {@link DimensionKey} — the single writer (and reader) of the byte
 * layout the key wraps (ADR 0008). One instance is a reusable encoder (scratch buffer + msgpack
 * writer); the decode side is static and only runs at the serving edge, per column, on demand.
 *
 * <p><b>The layout is durable identity — never change it.</b> It is byte-identical to what the
 * previous {@code UnpackedObject} flyweight ({@code DimensionKeyValue} over {@code DimensionValue}
 * elements) wrote, pinned by {@code DimensionKeyWireFormatGoldenTest}: a 1-entry msgpack map {@code
 * {"v": [...]}} whose array holds one kind-tagged element per column in schema order, each a
 * 3-entry map {@code {"k": kind, "s": string-or-"", "n": long-or-0}} with minimal-length integer
 * encoding. Sealed shuffle segments and durable rollup cells hold exactly these bytes.
 */
final class DimensionKeyWire {

  static final int KIND_NULL = 0;
  static final int KIND_STRING = 1;
  static final int KIND_LONG = 2;
  static final int KIND_INT = 3;
  static final int KIND_BOOLEAN = 4;

  private static final DirectBuffer VALUES_KEY = constant("v");
  private static final DirectBuffer KIND_KEY = constant("k");
  private static final DirectBuffer STRING_KEY = constant("s");
  private static final DirectBuffer NUMBER_KEY = constant("n");
  private static final DirectBuffer EMPTY = new UnsafeBuffer(0, 0);

  private final ExpandableArrayBuffer scratch = new ExpandableArrayBuffer(64);
  private final MsgPackWriter writer = new MsgPackWriter();
  // Reused to wrap a String's UTF-8 bytes for the msgpack writer without an extra copy step.
  private final UnsafeBuffer stringWrap = new UnsafeBuffer(0, 0);

  private static DirectBuffer constant(final String name) {
    return new UnsafeBuffer(name.getBytes(StandardCharsets.UTF_8));
  }

  /** Starts a new key encoding of {@code columns} elements into the reusable scratch buffer. */
  void begin(final int columns) {
    writer.wrap(scratch, 0);
    writer.writeMapHeader(1);
    writer.writeString(VALUES_KEY);
    writer.writeArrayHeader(columns);
  }

  void addNull() {
    element(KIND_NULL, EMPTY, 0L);
  }

  void addString(final String value) {
    stringWrap.wrap(value.getBytes(StandardCharsets.UTF_8));
    element(KIND_STRING, stringWrap, 0L);
  }

  /** Adds a string column straight from its UTF-8 bytes — no {@code String} materialization. */
  void addUtf8(final byte[] utf8) {
    stringWrap.wrap(utf8);
    element(KIND_STRING, stringWrap, 0L);
  }

  void addLong(final long value) {
    element(KIND_LONG, EMPTY, value);
  }

  void addInt(final int value) {
    element(KIND_INT, EMPTY, value);
  }

  void addBoolean(final boolean value) {
    element(KIND_BOOLEAN, EMPTY, value ? 1L : 0L);
  }

  private void element(final int kind, final DirectBuffer string, final long number) {
    writer.writeMapHeader(3);
    writer.writeString(KIND_KEY);
    writer.writeInteger(kind);
    writer.writeString(STRING_KEY);
    writer.writeString(string);
    writer.writeString(NUMBER_KEY);
    writer.writeInteger(number);
  }

  /** The number of encoded bytes since {@link #begin}. */
  int length() {
    return writer.getOffset();
  }

  /** The scratch array holding the encoded bytes {@code [0, length())} until the next encode. */
  byte[] array() {
    return scratch.byteArray();
  }

  /** A fresh, exact-length copy of the encoded bytes — an owned key's backing array. */
  byte[] copyBytes() {
    final byte[] out = new byte[length()];
    scratch.getBytes(0, out);
    return out;
  }

  /**
   * Decodes every column of an encoded key back to its logical value ({@code String}/{@code
   * Long}/{@code Integer}/{@code Boolean}, or {@code null} for the unknown bucket). The serving
   * edge's one remaining UTF-8 decode; the hot path never calls this.
   */
  static Object[] decode(
      final DimensionSchema schema, final byte[] encoded, final int offset, final int length) {
    final MsgPackReader reader = new MsgPackReader();
    reader.wrap(new UnsafeBuffer(encoded, offset, length), 0, length);
    if (reader.readMapHeader() != 1) {
      throw new IllegalArgumentException("malformed dimension key: expected a 1-entry map");
    }
    expectPropertyName(reader, 'v');
    final int columns = reader.readArrayHeader();
    if (columns != schema.size()) {
      throw new IllegalArgumentException(
          "expected " + schema.size() + " encoded values for " + schema + " but got " + columns);
    }
    final Object[] values = new Object[columns];
    for (int i = 0; i < columns; i++) {
      values[i] = decodeElement(reader);
    }
    return values;
  }

  /**
   * One kind-tagged element, written by {@link #element} in the fixed {@code k, s, n} property
   * order (the layout has a single writer, so the order is part of the canonical form).
   */
  private static Object decodeElement(final MsgPackReader reader) {
    if (reader.readMapHeader() != 3) {
      throw new IllegalArgumentException("malformed dimension key element: expected a 3-entry map");
    }
    expectPropertyName(reader, 'k');
    final long kind = reader.readInteger();
    expectPropertyName(reader, 's');
    final int stringLength = reader.readStringLength();
    String string = null;
    if (kind == KIND_STRING) {
      final byte[] utf8 = new byte[stringLength];
      reader.getBuffer().getBytes(reader.getOffset(), utf8);
      string = new String(utf8, StandardCharsets.UTF_8);
    }
    reader.skipBytes(stringLength);
    expectPropertyName(reader, 'n');
    final long number = reader.readInteger();
    return switch ((int) kind) {
      case KIND_NULL -> null;
      case KIND_STRING -> string;
      case KIND_LONG -> number;
      case KIND_INT -> (int) number;
      case KIND_BOOLEAN -> number != 0L;
      default -> throw new IllegalArgumentException("unknown dimension value kind: " + kind);
    };
  }

  private static void expectPropertyName(final MsgPackReader reader, final char name) {
    final int length = reader.readStringLength();
    final byte first = reader.getBuffer().getByte(reader.getOffset());
    reader.skipBytes(length);
    if (length != 1 || first != (byte) name) {
      throw new IllegalArgumentException(
          "malformed dimension key: expected property '" + name + "'");
    }
  }
}
