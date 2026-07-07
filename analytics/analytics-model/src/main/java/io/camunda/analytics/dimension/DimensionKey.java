/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;

/**
 * A value-equal tuple of dimension values conforming to a {@link DimensionSchema} — the grouping
 * key the combiner buffers by and the durable rollup stores. The key <em>is</em> its canonical
 * serialized form (ADR 0008): it wraps the encoded bytes of the {@link DimensionKeyWire} layout
 * (kind-tagged msgpack, schema order), identity is byte equality (UTF-8 byte equality is string
 * equality), and the hash is computed once. That makes every hop free of per-column serde: the
 * selector encodes straight off the fact, the shuffle and the RocksDB cell key reuse the same
 * bytes, and Stage 2 wraps received bytes without decoding.
 *
 * <p>{@link #values()}/{@link #get} decode lazily (once, memoized) — only the serving edge, which
 * renders {@code cell_key} strings and binds JDBC parameters, ever materializes column values. A
 * {@code null} value is the "unknown"/absent bucket for that dimension.
 */
public final class DimensionKey {

  private final DimensionSchema schema;

  // The canonical encoded bytes. An owned key holds an exact-length array it never mutates; the
  // selector's reusable probe key re-points these fields at its encoder scratch (same class, so
  // byte-wise equals makes a probe-vs-owned map lookup correct).
  private byte[] encoded;
  private int offset;
  private int length;
  private int hash;

  // Lazily decoded column values, memoized for the serving edge. Not part of identity.
  private List<Object> decoded;

  /** An owned key over an exact-length encoded array (never mutated afterwards). */
  private DimensionKey(final DimensionSchema schema, final byte[] encoded) {
    this.schema = schema;
    this.encoded = encoded;
    offset = 0;
    length = encoded.length;
    hash = hash(schema, encoded, 0, encoded.length);
  }

  /** An unbound probe view; {@link #wrapView} points it at an encoder's scratch buffer. */
  private DimensionKey(final DimensionSchema schema) {
    this.schema = schema;
  }

  public static DimensionKey of(final DimensionSchema schema, final Object... values) {
    return of(schema, Arrays.asList(values));
  }

  public static DimensionKey of(final DimensionSchema schema, final List<?> values) {
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(values, "values");
    if (values.size() != schema.size()) {
      throw new IllegalArgumentException(
          "expected " + schema.size() + " values for " + schema + " but got " + values.size());
    }
    final DimensionKeyWire wire = new DimensionKeyWire();
    wire.begin(schema.size());
    for (int i = 0; i < values.size(); i++) {
      encode(wire, schema.column(i), values.get(i));
    }
    return new DimensionKey(schema, wire.copyBytes());
  }

  /** Encodes one validated column value; the type check preserves {@code of}'s strictness. */
  private static void encode(
      final DimensionKeyWire wire, final DimensionColumn column, final Object value) {
    if (value == null) {
      wire.addNull();
      return;
    }
    switch (column.type()) {
      case STRING, TEXT -> {
        if (value instanceof final String string) {
          wire.addString(string);
        } else if (value instanceof final Utf8View view) {
          wire.addUtf8(view.utf8());
        } else {
          throw typeMismatch(column, value);
        }
      }
      case LONG -> {
        if (value instanceof final Long longValue) {
          wire.addLong(longValue);
        } else {
          throw typeMismatch(column, value);
        }
      }
      case INT -> {
        if (value instanceof final Integer intValue) {
          wire.addInt(intValue);
        } else {
          throw typeMismatch(column, value);
        }
      }
      case BOOLEAN -> {
        if (value instanceof final Boolean booleanValue) {
          wire.addBoolean(booleanValue);
        } else {
          throw typeMismatch(column, value);
        }
      }
    }
  }

  static IllegalArgumentException typeMismatch(final DimensionColumn column, final Object value) {
    return new IllegalArgumentException(
        "value "
            + value
            + " ("
            + value.getClass().getSimpleName()
            + ") is not "
            + column.type()
            + " for dimension '"
            + column.name()
            + "'");
  }

  /**
   * Wraps already-canonical encoded bytes into an owned key <em>without</em> decoding columns — the
   * Stage-2/recover path (the schema comes from the owning cube). The caller hands over ownership
   * of {@code encoded}; it must be exact-length and never mutated afterwards.
   */
  static DimensionKey fromEncoded(final DimensionSchema schema, final byte[] encoded) {
    return new DimensionKey(schema, encoded);
  }

  /** A reusable probe view for map lookups; see {@link DimensionKeySelector}. */
  static DimensionKey view(final DimensionSchema schema) {
    return new DimensionKey(schema);
  }

  /** Re-points a probe view at {@code [offset, offset+length)} of a scratch buffer. */
  void wrapView(final byte[] buffer, final int viewOffset, final int viewLength) {
    encoded = buffer;
    offset = viewOffset;
    length = viewLength;
    hash = hash(schema, buffer, viewOffset, viewLength);
    decoded = null;
  }

  /** An owned, storable copy of a probe view (or {@code this} if already owned). */
  DimensionKey toOwned() {
    return new DimensionKey(schema, copyEncoded());
  }

  private static int hash(
      final DimensionSchema schema, final byte[] bytes, final int offset, final int length) {
    int result = schema.hashCode();
    for (int i = offset; i < offset + length; i++) {
      result = 31 * result + bytes[i];
    }
    return result;
  }

  public DimensionSchema schema() {
    return schema;
  }

  public List<Object> values() {
    if (decoded == null) {
      decoded =
          Collections.unmodifiableList(
              Arrays.asList(DimensionKeyWire.decode(schema, encoded, offset, length)));
    }
    return decoded;
  }

  public Object get(final int index) {
    return values().get(index);
  }

  /** The value for the named dimension; throws if this key's schema has no such column. */
  public Object get(final String name) {
    final int index = schema.indexOf(name);
    if (index < 0) {
      throw new IllegalArgumentException("no dimension '" + name + "' in " + schema);
    }
    return values().get(index);
  }

  /** The length of the canonical encoded form. */
  int encodedLength() {
    return length;
  }

  /** Copies the canonical encoded form into {@code buffer} at {@code bufferOffset}. */
  void writeEncoded(final MutableDirectBuffer buffer, final int bufferOffset) {
    buffer.putBytes(bufferOffset, encoded, offset, length);
  }

  /** A fresh, exact-length copy of the canonical encoded form. */
  byte[] copyEncoded() {
    return Arrays.copyOfRange(encoded, offset, offset + length);
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final DimensionKey other
        && hash == other.hash
        && schema.equals(other.schema)
        && Arrays.equals(
            encoded,
            offset,
            offset + length,
            other.encoded,
            other.offset,
            other.offset + other.length);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public String toString() {
    return "DimensionKey" + values();
  }
}
