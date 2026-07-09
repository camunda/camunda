/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The wire/durable form of a {@link CompositeAggregateFunction} accumulator (ADR 0009): {@code
 * slotCount(4) ++ [slotLength(4) ++ slotBytes]*}, framing explicitly big-endian (matching the
 * durable cell framing convention), each slot delegating to that meter's own accumulator codec. One
 * instance per owner, like every {@link RecordValue} flyweight — the per-slot codecs it holds are
 * its own fresh instances.
 *
 * <p>Decoding is tolerant in both directions: a payload with <em>fewer</em> slots than declared
 * leaves the trailing slots {@code null} (identity on merge — the seam the parked meter-evolution
 * work builds on), and extra trailing slots are ignored. A zero-length slot decodes to {@code
 * null}.
 *
 * <p>{@link #fromBytesForMerge} decodes each slot through the slot codec's own merge-view path, so
 * a sketch slot stays a read-only wrap instead of heapifying — the composite it returns follows the
 * same contract: merge-only, never stored or re-serialized.
 */
public final class CompositeAccumulatorValue implements RecordValue<Object[]> {

  private final RecordValue<Object>[] slots;

  // Write side: the per-slot encodings of the last wrapValue. Read side: the last wrapped value.
  private byte[][] encoded;
  private Object[] value;

  @SuppressWarnings("unchecked")
  public CompositeAccumulatorValue(final List<BoundMeter<?, ?>> meters) {
    slots =
        meters.stream()
            .map(meter -> (RecordValue<Object>) meter.accumulatorCodec())
            .toArray(RecordValue[]::new);
  }

  @Override
  public RecordValue<Object[]> wrapValue(final Object[] composite) {
    final byte[][] encoded = new byte[slots.length][];
    for (int i = 0; i < slots.length; i++) {
      final Object slot = i < composite.length ? composite[i] : null;
      encoded[i] = slot == null ? EMPTY : slots[i].toBytes(slot);
    }
    this.encoded = encoded;
    value = composite;
    return this;
  }

  @Override
  public Object[] value() {
    return value;
  }

  @Override
  public int getLength() {
    int length = Integer.BYTES;
    for (final byte[] slot : encoded) {
      length += Integer.BYTES + slot.length;
    }
    return length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    int position = offset;
    buffer.putInt(position, encoded.length, ByteOrder.BIG_ENDIAN);
    position += Integer.BYTES;
    for (final byte[] slot : encoded) {
      buffer.putInt(position, slot.length, ByteOrder.BIG_ENDIAN);
      position += Integer.BYTES;
      buffer.putBytes(position, slot);
      position += slot.length;
    }
    return position - offset;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    value = decode(buffer, offset, false);
  }

  @Override
  public Object[] fromBytesForMerge(final byte[] bytes) {
    return decode(new UnsafeBuffer(bytes), 0, true);
  }

  private Object[] decode(final DirectBuffer buffer, final int offset, final boolean forMerge) {
    int position = offset;
    final int count = buffer.getInt(position, ByteOrder.BIG_ENDIAN);
    position += Integer.BYTES;
    final Object[] composite = new Object[slots.length];
    for (int i = 0; i < count; i++) {
      final int length = buffer.getInt(position, ByteOrder.BIG_ENDIAN);
      position += Integer.BYTES;
      if (i < slots.length && length > 0) {
        // Per-slot byte copy: the slot codecs' byte[] entry points need a self-contained array.
        // The merge path still wins where it matters — a sketch slot wraps these bytes read-only
        // instead of heapifying the sketch.
        final byte[] slot = new byte[length];
        buffer.getBytes(position, slot);
        composite[i] = forMerge ? slots[i].fromBytesForMerge(slot) : slots[i].fromBytes(slot);
      }
      position += length;
    }
    return composite;
  }

  /**
   * The raw per-slot encodings of one composite payload, aligned to the declared meter order — the
   * serving writers' seam: each backend decodes only the slots it needs (pushdown decompose, blob,
   * finalized scalar) without constructing accumulators for the rest. Slots absent from an
   * older/shorter payload come back as {@code null}; a zero-length slot is {@code null} too.
   */
  public static List<byte[]> slotBytes(final byte[] composite, final int declaredSlots) {
    final UnsafeBuffer buffer = new UnsafeBuffer(composite);
    int position = 0;
    final int count = buffer.getInt(position, ByteOrder.BIG_ENDIAN);
    position += Integer.BYTES;
    final List<byte[]> slots = new ArrayList<>(declaredSlots);
    for (int i = 0; i < count; i++) {
      final int length = buffer.getInt(position, ByteOrder.BIG_ENDIAN);
      position += Integer.BYTES;
      if (i < declaredSlots) {
        if (length == 0) {
          slots.add(null);
        } else {
          final byte[] slot = new byte[length];
          buffer.getBytes(position, slot);
          slots.add(slot);
        }
      }
      position += length;
    }
    while (slots.size() < declaredSlots) {
      slots.add(null);
    }
    return slots;
  }

  private static final byte[] EMPTY = new byte[0];
}
