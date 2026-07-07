/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * The {@link RecordValue} codec for {@link DimensionKey}. Since the key <em>is</em> its canonical
 * serialized form (ADR 0008), this codec is a pass-through: {@code toBytes}/{@code write} copy the
 * key's already-encoded bytes, and {@code wrap}/{@code fromBytes} wrap received bytes into a key
 * <b>without any per-column decode</b> — the bound {@link DimensionSchema} (supplied at
 * construction, from the owning cube) travels with the key for the serving edge's lazy decode. The
 * wire layout is unchanged from the previous {@code UnpackedObject} flyweight (pinned by {@code
 * DimensionKeyWireFormatGoldenTest}), so sealed segments and durable cells written before the
 * byte-backed refactor read back identically. One instance is mutable and reused — give each
 * consumer its own.
 */
public final class DimensionKeyValue implements RecordValue<DimensionKey> {

  private final DimensionSchema schema;
  private DimensionKey key;

  public DimensionKeyValue(final DimensionSchema schema) {
    this.schema = Objects.requireNonNull(schema, "schema");
  }

  @Override
  public DimensionKeyValue wrapValue(final DimensionKey value) {
    key = checkSchema(value);
    return this;
  }

  @Override
  public DimensionKey value() {
    return key;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] encoded = new byte[length];
    buffer.getBytes(offset, encoded);
    key = DimensionKey.fromEncoded(schema, encoded);
  }

  @Override
  public int getLength() {
    return key.encodedLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    key.writeEncoded(buffer, offset);
    return key.encodedLength();
  }

  /** The key's canonical encoded bytes, as a fresh owned copy. */
  @Override
  public byte[] toBytes(final DimensionKey value) {
    return checkSchema(value).copyEncoded();
  }

  /**
   * Wraps {@code bytes} into a byte-backed key without decoding columns, taking ownership of the
   * array (every caller hands a freshly materialized copy — a decoded shuffle cell, a store read).
   */
  @Override
  public DimensionKey fromBytes(final byte[] bytes) {
    return DimensionKey.fromEncoded(schema, bytes);
  }

  private DimensionKey checkSchema(final DimensionKey value) {
    if (!schema.equals(value.schema())) {
      throw new IllegalArgumentException(
          "key schema " + value.schema() + " does not match flyweight schema " + schema);
    }
    return value;
  }
}
